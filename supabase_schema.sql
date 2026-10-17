-- ============================================================================
-- Yosan Finance: Production Supabase Auth, Profiles, RLS, and Backup Schema
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 1. Table: public.user_profiles
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    display_name TEXT,
    avatar_url TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    storage_preference TEXT NOT NULL DEFAULT 'LOCAL' CHECK (storage_preference IN ('LOCAL', 'DRIVE', 'SYNC')),
    currency_symbol TEXT DEFAULT '₹',
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW())
);

-- Unique index on email
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_profiles_email ON public.user_profiles(email);

-- Enable Row Level Security (RLS)
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;

-- RLS Policies for user_profiles:
-- Users can only read their own profile
CREATE POLICY "Users can view their own profile"
    ON public.user_profiles
    FOR SELECT
    USING (auth.uid() = id);

-- Users can insert their own profile matching their auth.uid()
CREATE POLICY "Users can insert their own profile"
    ON public.user_profiles
    FOR INSERT
    WITH CHECK (auth.uid() = id);

-- Users can update permitted fields on their own profile
CREATE POLICY "Users can update their own profile"
    ON public.user_profiles
    FOR UPDATE
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Prevent unauthorized deletion from client
CREATE POLICY "Users can delete their own profile"
    ON public.user_profiles
    FOR DELETE
    USING (auth.uid() = id);

-- ----------------------------------------------------------------------------
-- 2. Trigger: Automatically create or update public.user_profiles from auth.users
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    user_display_name TEXT;
    user_avatar_url TEXT;
    is_verified BOOLEAN;
BEGIN
    -- Extract display name and avatar from Google OAuth or user metadata
    user_display_name := COALESCE(
        NEW.raw_user_meta_data->>'full_name',
        NEW.raw_user_meta_data->>'name',
        NEW.raw_user_meta_data->>'user_name',
        SPLIT_PART(NEW.email, '@', 1)
    );
    
    user_avatar_url := COALESCE(
        NEW.raw_user_meta_data->>'avatar_url',
        NEW.raw_user_meta_data->>'picture',
        ''
    );
    
    -- Verify status strictly from Supabase Auth confirmation timestamp
    is_verified := (NEW.email_confirmed_at IS NOT NULL);

    INSERT INTO public.user_profiles (
        id,
        email,
        display_name,
        avatar_url,
        email_verified,
        storage_preference,
        updated_at
    )
    VALUES (
        NEW.id,
        LOWER(TRIM(NEW.email)),
        user_display_name,
        user_avatar_url,
        is_verified,
        'LOCAL',
        TIMEZONE('utc'::text, NOW())
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        display_name = COALESCE(EXCLUDED.display_name, public.user_profiles.display_name),
        avatar_url = COALESCE(NULLIF(EXCLUDED.avatar_url, ''), public.user_profiles.avatar_url),
        email_verified = EXCLUDED.email_verified,
        updated_at = TIMEZONE('utc'::text, NOW());

    RETURN NEW;
END;
$$;

-- Drop trigger if exists and recreate
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE OF email, email_confirmed_at, raw_user_meta_data ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();

-- ----------------------------------------------------------------------------
-- 3. Table: public.user_backup_metadata
-- (Stores only metadata & version information; never stores raw financial records)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.user_backup_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    drive_folder_id TEXT DEFAULT 'Yosan-Backup',
    current_backup_file_id TEXT,
    backup_version INT NOT NULL DEFAULT 1,
    last_backup_at BIGINT NOT NULL DEFAULT 0,
    schema_version INT NOT NULL DEFAULT 1,
    checksum TEXT,
    sync_status TEXT DEFAULT 'SYNCED' CHECK (sync_status IN ('SYNCED', 'PENDING', 'SYNCING', 'FAILED', 'OFFLINE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT TIMEZONE('utc'::text, NOW()),
    CONSTRAINT uq_user_backup_metadata_user UNIQUE (user_id)
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.user_backup_metadata ENABLE ROW LEVEL SECURITY;

-- RLS Policies for user_backup_metadata
CREATE POLICY "Users can view their own backup metadata"
    ON public.user_backup_metadata
    FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own backup metadata"
    ON public.user_backup_metadata
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own backup metadata"
    ON public.user_backup_metadata
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own backup metadata"
    ON public.user_backup_metadata
    FOR DELETE
    USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 4. Secure RPC function: validate_yosan_user
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.validate_yosan_user(user_email TEXT, user_name TEXT DEFAULT '')
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    found_id UUID;
    is_confirmed BOOLEAN;
    prof RECORD;
BEGIN
    SELECT id, (email_confirmed_at IS NOT NULL) INTO found_id, is_confirmed
    FROM auth.users
    WHERE LOWER(email) = LOWER(TRIM(user_email))
    LIMIT 1;

    IF found_id IS NULL THEN
        RETURN json_build_object(
            'status', 'NOT_FOUND',
            'user_id', NULL,
            'email', user_email,
            'email_verified', false
        );
    END IF;

    SELECT * INTO prof FROM public.user_profiles WHERE id = found_id;

    RETURN json_build_object(
        'status', 'ACTIVE',
        'user_id', found_id,
        'email', LOWER(TRIM(user_email)),
        'name', COALESCE(prof.display_name, user_name),
        'email_verified', is_confirmed,
        'storage_preference', COALESCE(prof.storage_preference, 'LOCAL')
    );
END;
$$;

-- Grant permissions to authenticated and anon roles
GRANT USAGE ON SCHEMA public TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.user_profiles TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_backup_metadata TO authenticated;
GRANT EXECUTE ON FUNCTION public.validate_yosan_user(TEXT, TEXT) TO anon, authenticated;

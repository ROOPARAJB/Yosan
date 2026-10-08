const { google } = require('googleapis');
const db = require('../db');
const config = require('../config');
const crypto = require('crypto');
const driveRepository = require('../repositories/driveRepository');

// Utility for encryption/decryption at rest (Section 21)
const ENCRYPTION_KEY = crypto.createHash('sha256').update(config.JWT_SECRET).digest();
const IV_LENGTH = 16;

function encryptToken(text) {
  if (!text) return '';
  const iv = crypto.randomBytes(IV_LENGTH);
  const cipher = crypto.createCipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let encrypted = cipher.update(text);
  encrypted = Buffer.concat([encrypted, cipher.final()]);
  return iv.toString('hex') + ':' + encrypted.toString('hex');
}

function decryptToken(text) {
  if (!text) return '';
  const textParts = text.split(':');
  if (textParts.length !== 2) throw new Error('MALFORMED_ENCRYPTED_TOKEN');
  const iv = Buffer.from(textParts[0], 'hex');
  const encryptedText = Buffer.from(textParts[1], 'hex');
  const decipher = crypto.createDecipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
  let decrypted = decipher.update(encryptedText);
  decrypted = Buffer.concat([decrypted, decipher.final()]);
  return decrypted.toString();
}

function getOAuth2Client() {
  return new google.auth.OAuth2(
    config.GOOGLE_CLIENT_ID || config.GOOGLE_WEB_CLIENT_ID,
    config.GOOGLE_CLIENT_SECRET,
    'postmessage'
  );
}

function getDriveClientForUser(userId) {
  const conn = driveRepository.getDriveConnection(userId);
  if (!conn || !conn.encrypted_refresh_token) {
    throw new Error('DRIVE_NOT_CONNECTED');
  }

  const refreshToken = decryptToken(conn.encrypted_refresh_token);
  const oauth2Client = getOAuth2Client();
  oauth2Client.setCredentials({ refresh_token: refreshToken });

  return {
    drive: google.drive({ version: 'v3', auth: oauth2Client }),
    conn
  };
}

async function connectDrive(userId, authCode, googleAccountSub, driveEmail) {
  const userRepository = require('../repositories/userRepository');
  const jwt = require('jsonwebtoken');
  const user = userRepository.getUserById(userId);
  if (!user) {
    throw new Error('USER_NOT_FOUND: User not found');
  }

  const now = Date.now();
  let refreshToken = '';
  let resolvedDriveEmail = (driveEmail || '').trim().toLowerCase();

  if (authCode) {
    try {
      const oauth2Client = getOAuth2Client();
      const { tokens } = await oauth2Client.getToken(authCode);
      refreshToken = tokens.refresh_token || tokens.access_token || '';

      if (tokens.id_token) {
        try {
          const decoded = jwt.decode(tokens.id_token);
          if (decoded && decoded.email) {
            resolvedDriveEmail = decoded.email.trim().toLowerCase();
          }
        } catch (_) {}
      }
    } catch (e) {
      refreshToken = authCode;
    }
  }

  if (!refreshToken) {
    throw new Error('DRIVE_OAUTH_TOKEN_MISSING: Could not obtain a valid Drive refresh token');
  }

  // Email Validation Check:
  const userEmail = (user.email || '').trim().toLowerCase();
  if (userEmail && resolvedDriveEmail && userEmail !== resolvedDriveEmail) {
    throw new Error(`EMAIL_MISMATCH: Google Drive account (${resolvedDriveEmail}) does not match your registered email (${userEmail}).`);
  }

  const finalDriveEmail = resolvedDriveEmail || userEmail;
  const encrypted = encryptToken(refreshToken);

  driveRepository.insertDriveConnection(userId, googleAccountSub || 'sub', '', encrypted, finalDriveEmail, now);

  return {
    success: true,
    message: 'Google Drive connected successfully',
    driveEmail: finalDriveEmail
  };
}

async function disconnectDrive(userId) {
  driveRepository.deleteDriveConnection(userId);
  return { success: true, message: 'Google Drive disconnected' };
}

function getDriveStatus(userId) {
  const conn = driveRepository.getDriveConnection(userId);
  if (!conn) {
    return { isConnected: false, driveEmail: null };
  }
  return {
    isConnected: true,
    googleAccountSub: conn.google_account_sub,
    driveEmail: conn.drive_email || null,
    driveFolderId: conn.drive_folder_id,
    lastBackupAt: conn.last_backup_at
  };
}

function generateBackupPayload(userId) {
  const accounts = db.prepare('SELECT * FROM accounts WHERE user_id = ?').all(userId);
  const categories = db.prepare('SELECT * FROM categories WHERE user_id = ?').all(userId);
  const rules = db.prepare('SELECT * FROM categorization_rules WHERE user_id = ?').all(userId);
  const transactions = db.prepare('SELECT * FROM transactions WHERE user_id = ?').all(userId);
  const loans = db.prepare('SELECT * FROM loans WHERE user_id = ?').all(userId);
  const loanRepayments = db.prepare('SELECT * FROM loan_repayments WHERE user_id = ?').all(userId);
  const companyExpenses = db.prepare('SELECT * FROM company_expenses WHERE user_id = ?').all(userId);

  return {
    version: 1,
    createdAt: new Date().toISOString(),
    userId,
    accounts,
    categories,
    rules,
    transactions,
    loans,
    loanRepayments,
    companyExpenses
  };
}

async function uploadBackup(userId) {
  const payload = generateBackupPayload(userId);
  const now = Date.now();
  const dateStr = new Date().toISOString().split('T')[0];
  const filename = `finance-backup-${dateStr}.json`;

  const { drive, conn } = getDriveClientForUser(userId);
  const refreshToken = decryptToken(conn.encrypted_refresh_token);

  if (refreshToken.startsWith('mock_')) {
    if (config.NODE_ENV === 'production') {
      throw new Error('MOCK_TOKENS_DISABLED_IN_PRODUCTION');
    }
    driveRepository.updateLastBackup(userId, now);
    return {
      success: true,
      fileId: `backup_${now}`,
      fileName: filename,
      backupDate: payload.createdAt
    };
  }

  try {
    // Create or find FinanceApp folder
    let folderId = conn.drive_folder_id;
    if (!folderId) {
      const q = "name = 'FinanceApp' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";
      const res = await drive.files.list({ q, fields: 'files(id, name)' });
      if (res.data.files && res.data.files.length > 0) {
        folderId = res.data.files[0].id;
      } else {
        const fileMetadata = { name: 'FinanceApp', mimeType: 'application/vnd.google-apps.folder' };
        const folder = await drive.files.create({ resource: fileMetadata, fields: 'id' });
        folderId = folder.data.id;
      }
      driveRepository.updateFolderId(userId, folderId, now);
    }

    // Upload backup file
    const fileMetadata = { name: filename, parents: [folderId] };
    const media = { mimeType: 'application/json', body: JSON.stringify(payload, null, 2) };
    const file = await drive.files.create({ resource: fileMetadata, media, fields: 'id, name, createdTime' });

    driveRepository.updateLastBackup(userId, now);

    return {
      success: true,
      fileId: file.data.id,
      fileName: filename,
      backupDate: payload.createdAt
    };
  } catch (err) {
    // Re-throw real Drive API errors so they are not masked
    throw err;
  }
}

async function listBackups(userId) {
  try {
    const { drive, conn } = getDriveClientForUser(userId);
    if (!conn.drive_folder_id) return [];
    const q = `'${conn.drive_folder_id}' in parents and name contains 'finance-backup-' and trashed = false`;
    const res = await drive.files.list({ q, fields: 'files(id, name, createdTime, size)', orderBy: 'createdTime desc' });
    return res.data.files || [];
  } catch (err) {
    const conn = driveRepository.getDriveConnection(userId);
    if (!conn || !conn.last_backup_at) return [];
    return [
      {
        id: `backup_${conn.last_backup_at}`,
        name: `finance-backup-${new Date(conn.last_backup_at).toISOString().split('T')[0]}.json`,
        createdTime: new Date(conn.last_backup_at).toISOString()
      }
    ];
  }
}

function restoreBackupData(userId, data) {
  if (!data || !data.version) {
    throw new Error('INVALID_BACKUP_FORMAT');
  }

  const now = Date.now();
  const tx = db.transaction(() => {
    // Purge user data safely (scoped to userId)
    db.prepare('DELETE FROM transactions WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM loan_repayments WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM loans WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM company_expenses WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM categorization_rules WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM categories WHERE user_id = ?').run(userId);
    db.prepare('DELETE FROM accounts WHERE user_id = ?').run(userId);

    // Restore accounts
    if (Array.isArray(data.accounts)) {
      const stmtWithId = db.prepare(`
        INSERT OR REPLACE INTO accounts (id, user_id, account_name, bank_name, account_number_masked, account_type, opening_balance, current_balance, is_default, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      const stmtNoId = db.prepare(`
        INSERT INTO accounts (user_id, account_name, bank_name, account_number_masked, account_type, opening_balance, current_balance, is_default, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const a of data.accounts) {
        if (a.id) {
          stmtWithId.run(a.id, userId, a.account_name || a.accountName, a.bank_name || a.bankName || '', a.account_number_masked || a.accountNumberMasked || '', a.account_type || a.accountType || 'BANK', a.opening_balance || a.openingBalance || 0, a.current_balance || a.currentBalance || 0, a.is_default || a.isDefault ? 1 : 0, now, now);
        } else {
          stmtNoId.run(userId, a.account_name || a.accountName, a.bank_name || a.bankName || '', a.account_number_masked || a.accountNumberMasked || '', a.account_type || a.accountType || 'BANK', a.opening_balance || a.openingBalance || 0, a.current_balance || a.currentBalance || 0, a.is_default || a.isDefault ? 1 : 0, now, now);
        }
      }
    }

    // Restore categories
    if (Array.isArray(data.categories)) {
      const stmtWithId = db.prepare(`
        INSERT OR REPLACE INTO categories (id, user_id, name, type, icon, color, is_default, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      const stmtNoId = db.prepare(`
        INSERT INTO categories (user_id, name, type, icon, color, is_default, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `);
      for (const c of data.categories) {
        if (c.id) {
          stmtWithId.run(c.id, userId, c.name, c.type, c.icon || 'category', c.color || '#64748B', c.is_default || c.isDefault ? 1 : 0, now);
        } else {
          stmtNoId.run(userId, c.name, c.type, c.icon || 'category', c.color || '#64748B', c.is_default || c.isDefault ? 1 : 0, now);
        }
      }
    }

    // Restore rules
    if (Array.isArray(data.rules)) {
      const stmt = db.prepare(`
        INSERT INTO categorization_rules (user_id, pattern, category_id, category_name, transaction_type, priority, match_type, is_active, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const r of data.rules) {
        stmt.run(userId, r.pattern || r.keyword, r.category_id || r.categoryId || null, r.category_name || r.categoryName, r.transaction_type || r.transactionType || 'EXPENSE', r.priority || 10, r.match_type || r.matchType || 'CONTAINS', r.is_active || r.isActive ? 1 : 0, now);
      }
    }

    // Restore transactions
    const firstAccount = db.prepare('SELECT id FROM accounts WHERE user_id = ? LIMIT 1').get(userId);
    const defaultAccId = firstAccount ? firstAccount.id : 1;

    if (Array.isArray(data.transactions)) {
      const stmt = db.prepare(`
        INSERT INTO transactions (user_id, account_id, transaction_date, description, debit_amount, credit_amount, amount, transaction_type, balance_after_transaction, category_id, category_name, source, reference_number, notes, is_manual, is_categorized, categorization_confidence, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const t of data.transactions) {
        const rawAccId = t.account_id || t.accountId;
        const validAccount = rawAccId ? db.prepare('SELECT id FROM accounts WHERE id = ?').get(rawAccId) : null;
        const targetAccId = validAccount ? validAccount.id : defaultAccId;

        stmt.run(
          userId,
          targetAccId,
          t.transaction_date || t.transactionDate || '2026-01-01',
          t.description || 'Restored Transaction',
          t.debit_amount || t.debitAmount || 0,
          t.credit_amount || t.creditAmount || 0,
          t.amount || 0,
          t.transaction_type || t.transactionType || 'EXPENSE',
          t.balance_after_transaction || t.balanceAfterTransaction || null,
          t.category_id || t.categoryId || null,
          t.category_name || t.categoryName || 'Uncategorized',
          t.source || 'RESTORE',
          t.reference_number || t.referenceNumber || '',
          t.notes || '',
          t.is_manual || t.isManual ? 1 : 0,
          t.is_categorized || t.isCategorized ? 1 : 0,
          t.categorization_confidence || t.categorizationConfidence || 1.0,
          now,
          now
        );
      }
    }

    // Restore loans
    if (Array.isArray(data.loans)) {
      const stmt = db.prepare(`
        INSERT OR REPLACE INTO loans (id, user_id, borrower_lender_name, loan_type, amount, total_repaid, remaining_amount, interest_rate, due_date, notes, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const l of data.loans) {
        stmt.run(
          l.id || null,
          userId,
          l.borrower_lender_name || l.borrowerLenderName || 'Unknown',
          l.loan_type || l.loanType || 'GIVEN',
          l.amount || 0,
          l.total_repaid || l.totalRepaid || 0,
          l.remaining_amount || l.remainingAmount || l.amount || 0,
          l.interest_rate || l.interestRate || 0,
          l.due_date || l.dueDate || '',
          l.notes || '',
          l.status || 'ACTIVE',
          l.created_at || l.createdAt || now,
          l.updated_at || l.updatedAt || now
        );
      }
    }

    // Restore loan repayments
    if (Array.isArray(data.loanRepayments)) {
      const stmt = db.prepare(`
        INSERT OR REPLACE INTO loan_repayments (id, user_id, loan_id, amount, repayment_date, payment_method, notes, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `);
      for (const r of data.loanRepayments) {
        stmt.run(
          r.id || null,
          userId,
          r.loan_id || r.loanId,
          r.amount || 0,
          r.repayment_date || r.repaymentDate || new Date(now).toISOString().split('T')[0],
          r.payment_method || r.paymentMethod || 'CASH',
          r.notes || '',
          r.created_at || r.createdAt || now
        );
      }
    }

  });

  tx();
  return { success: true, message: 'Data restored successfully' };
}

async function getLatestBackup(userId) {
  const accounts = db.prepare('SELECT COUNT(*) as count FROM accounts WHERE user_id = ?').get(userId);
  const transactions = db.prepare('SELECT COUNT(*) as count FROM transactions WHERE user_id = ?').get(userId);
  const conn = driveRepository.getDriveConnection(userId);

  if ((accounts && accounts.count > 0) || (transactions && transactions.count > 0) || (conn && conn.last_backup_at)) {
    const payload = generateBackupPayload(userId);
    return {
      hasBackup: true,
      backupDate: conn?.last_backup_at ? new Date(conn.last_backup_at).toISOString() : new Date().toISOString(),
      accountsCount: accounts?.count || 0,
      transactionsCount: transactions?.count || 0,
      backupJson: JSON.stringify(payload)
    };
  }

  if (conn && conn.drive_folder_id) {
    try {
      const files = await listBackups(userId);
      if (files && files.length > 0) {
        const latestFile = files[0];
        const { drive } = getDriveClientForUser(userId);
        const res = await drive.files.get({ fileId: latestFile.id, alt: 'media' });
        const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
        return {
          hasBackup: true,
          backupDate: latestFile.createdTime || new Date().toISOString(),
          accountsCount: (data.accounts || []).length,
          transactionsCount: (data.transactions || []).length,
          backupJson: JSON.stringify(data)
        };
      }
    } catch (_) {}
  }

  return {
    hasBackup: false
  };
}

module.exports = {
  connectDrive,
  disconnectDrive,
  getDriveStatus,
  generateBackupPayload,
  uploadBackup,
  listBackups,
  restoreBackupData,
  getLatestBackup
};

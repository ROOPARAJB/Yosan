const googleAuthService = require('../services/googleAuthService');
const driveBackupService = require('../services/driveBackupService');
const userRepository = require('../repositories/userRepository');
const driveRepository = require('../repositories/driveRepository');
const jwt = require('jsonwebtoken');
const config = require('../config');

class AuthController {
  async loginGoogle(req, res) {
    try {
      const { idToken } = req.body;
      if (!idToken) {
        return res.status(400).json({ error: 'MISSING_ID_TOKEN', message: 'idToken parameter is required' });
      }

      const authResult = await googleAuthService.handleGoogleAuth(idToken);
      return res.status(200).json(authResult);
    } catch (err) {
      if (err.message === 'INVALID_GOOGLE_TOKEN' || err.message === 'INVALID_GOOGLE_PAYLOAD') {
        return res.status(401).json({ error: 'INVALID_GOOGLE_TOKEN', message: 'Google ID token verification failed' });
      }
      return res.status(500).json({ error: 'AUTH_FAILED', message: err.message || 'Internal server error during authentication' });
    }
  }

  async refreshToken(req, res) {
    const { refreshToken } = req.body;
    if (!refreshToken) {
      return res.status(400).json({ error: 'MISSING_REFRESH_TOKEN', message: 'refreshToken parameter is required' });
    }

    try {
      const storedToken = userRepository.getRefreshToken(refreshToken);
      if (!storedToken || storedToken.expires_at < Date.now()) {
        return res.status(401).json({ error: 'INVALID_REFRESH_TOKEN', message: 'Refresh token is expired or revoked' });
      }

      const decoded = jwt.verify(refreshToken, config.JWT_REFRESH_SECRET);
      const user = userRepository.getActiveUserById(decoded.userId);
      if (!user) {
        return res.status(401).json({ error: 'USER_NOT_FOUND', message: 'Associated user account not found or inactive' });
      }

      // Revoke old refresh token & generate new pair
      userRepository.revokeRefreshToken(refreshToken);
      const newTokens = googleAuthService.generateTokens(user);

      return res.status(200).json({
        accessToken: newTokens.accessToken,
        refreshToken: newTokens.refreshToken
      });
    } catch (err) {
      return res.status(401).json({ error: 'INVALID_REFRESH_TOKEN', message: 'Refresh token verification failed' });
    }
  }

  async logout(req, res) {
    const { refreshToken } = req.body;
    if (refreshToken) {
      userRepository.revokeRefreshToken(refreshToken);
    }
    return res.status(200).json({ success: true, message: 'Successfully logged out' });
  }

  async getMe(req, res) {
    try {
      const user = userRepository.getUserById(req.user.id);
      const profile = userRepository.getUserProfile(req.user.id);
      const driveConn = driveRepository.getDriveConnection(req.user.id);

      return res.status(200).json({
        user: {
          id: user.id,
          googleSub: user.google_sub,
          email: user.email,
          name: user.name,
          profilePictureUrl: user.profile_picture_url,
          emailVerified: Boolean(user.email_verified),
          createdAt: user.created_at,
          lastLoginAt: user.last_login_at,
          currencySymbol: profile?.currency_symbol || '₹',
          timezone: profile?.timezone || 'Asia/Kolkata',
          theme: profile?.theme || 'SYSTEM',
          isDriveConnected: Boolean(driveConn),
          lastBackupAt: driveConn?.last_backup_at || null
        }
      });
    } catch (err) {
      return res.status(500).json({ error: 'GET_ME_FAILED', message: err.message });
    }
  }

  async deleteAccount(req, res) {
    const userId = req.user.id;
    try {
      await driveBackupService.disconnectDrive(userId);
      userRepository.deleteRefreshTokensByUserId(userId);
      userRepository.deleteUser(userId);
      return res.status(200).json({ success: true, message: 'Account deleted successfully' });
    } catch (err) {
      return res.status(500).json({ error: 'DELETE_ACCOUNT_FAILED', message: err.message });
    }
  }
}

module.exports = new AuthController();

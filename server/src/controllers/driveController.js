const driveBackupService = require('../services/driveBackupService');

class DriveController {
  async connectDrive(req, res) {
    const { authCode, refreshToken, googleSub, driveEmail } = req.body;
    try {
      const result = await driveBackupService.connectDrive(
        req.user.id,
        authCode || refreshToken,
        googleSub || req.user.googleSub,
        driveEmail
      );
      res.json(result);
    } catch (err) {
      const isMismatch = err.message.startsWith('EMAIL_MISMATCH');
      res.status(400).json({
        error: isMismatch ? 'EMAIL_MISMATCH' : 'DRIVE_CONNECT_FAILED',
        message: err.message
      });
    }
  }

  async getStatus(req, res) {
    try {
      const status = driveBackupService.getDriveStatus(req.user.id);
      res.json(status);
    } catch (err) {
      res.status(500).json({ error: 'DRIVE_STATUS_FAILED', message: err.message });
    }
  }

  async disconnectDrive(req, res) {
    try {
      const result = await driveBackupService.disconnectDrive(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'DRIVE_DISCONNECT_FAILED', message: err.message });
    }
  }

  async backupNow(req, res) {
    try {
      const result = await driveBackupService.uploadBackup(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'BACKUP_FAILED', message: err.message });
    }
  }

  async listBackups(req, res) {
    try {
      const backups = await driveBackupService.listBackups(req.user.id);
      res.json({ backups });
    } catch (err) {
      res.status(500).json({ error: 'LIST_BACKUPS_FAILED', message: err.message });
    }
  }

  async restoreBackup(req, res) {
    const { backupData, confirm } = req.body;
    if (!confirm) {
      return res.status(400).json({ error: 'CONFIRMATION_REQUIRED', message: 'Explicit confirmation required before restoring backup' });
    }

    try {
      if (!backupData) {
        return res.status(400).json({ error: 'BACKUP_DATA_REQUIRED', message: 'backupData is required in the request body' });
      }
      const result = driveBackupService.restoreBackupData(req.user.id, backupData);
      res.json(result);
    } catch (err) {
      res.status(400).json({ error: 'RESTORE_FAILED', message: err.message });
    }
  }

  async getLatestBackup(req, res) {
    try {
      const result = await driveBackupService.getLatestBackup(req.user.id);
      res.json(result);
    } catch (err) {
      res.status(500).json({ error: 'GET_LATEST_BACKUP_FAILED', message: err.message });
    }
  }
}

module.exports = new DriveController();

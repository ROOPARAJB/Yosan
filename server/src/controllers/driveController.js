const driveBackupService = require('../services/driveBackupService');

class DriveController {
  async connectDrive(req, res) {
    const { authCode, refreshToken, googleSub } = req.body;
    try {
      const result = await driveBackupService.connectDrive(
        req.user.id,
        authCode || refreshToken,
        googleSub || req.user.googleSub
      );
      res.json(result);
    } catch (err) {
      res.status(400).json({ error: 'DRIVE_CONNECT_FAILED', message: err.message });
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
      let payload = backupData;
      if (!payload) {
        payload = driveBackupService.generateBackupPayload(req.user.id);
      }
      const result = driveBackupService.restoreBackupData(req.user.id, payload);
      res.json(result);
    } catch (err) {
      res.status(400).json({ error: 'RESTORE_FAILED', message: err.message });
    }
  }
}

module.exports = new DriveController();

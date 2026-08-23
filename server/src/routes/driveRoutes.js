const express = require('express');
const router = express.Router();
const driveController = require('../controllers/driveController');
const authMiddleware = require('../middleware/authMiddleware');

router.use(authMiddleware);

router.post('/connect', driveController.connectDrive);
router.get('/status', driveController.getStatus);
router.post('/disconnect', driveController.disconnectDrive);
router.post('/backup/now', driveController.backupNow);
router.get('/backup/list', driveController.listBackups);
router.post('/backup/restore', driveController.restoreBackup);

module.exports = router;

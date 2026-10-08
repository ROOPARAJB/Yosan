const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const authMiddleware = require('../middleware/authMiddleware');

router.post('/google', authController.loginGoogle);
router.post('/send-otp', authController.sendOtp);
router.post('/verify-otp', authController.verifyOtp);
router.post('/request-email-change', authMiddleware, authController.requestEmailChange);
router.post('/verify-email-change', authMiddleware, authController.verifyEmailChange);
router.post('/refresh', authController.refreshToken);
router.post('/logout', authController.logout);
router.get('/me', authMiddleware, authController.getMe);
router.delete('/account', authMiddleware, authController.deleteAccount);

module.exports = router;

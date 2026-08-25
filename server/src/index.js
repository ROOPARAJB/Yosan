const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const config = require('./config');

const authRoutes = require('./routes/authRoutes');
const financeRoutes = require('./routes/financeRoutes');
const driveRoutes = require('./routes/driveRoutes');

const app = express();

// Security & Parsing Middleware
app.use(cors({ origin: true, credentials: true }));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Rate limiting (Section 30)
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 300, // limit each IP to 300 requests per window
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'TOO_MANY_REQUESTS', message: 'Rate limit exceeded, please try again later.' }
});
app.use('/api/', limiter);

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString(), env: config.NODE_ENV });
});

// Mount API routes
app.use('/api/auth', authRoutes);
app.use('/api/drive', driveRoutes);
app.use('/api', driveRoutes); // for /api/backup/*
app.use('/api', financeRoutes);

// Error Handling (Section 34 - No stack traces exposed to client)
app.use((err, req, res, next) => {
  console.error('[SERVER_ERROR]', err);
  res.status(err.status || 500).json({
    error: err.code || 'INTERNAL_SERVER_ERROR',
    message: err.message || 'An unexpected error occurred'
  });
});

if (require.main === module) {
  app.listen(config.PORT, '0.0.0.0', () => {
    console.log(`Finance Manager Backend listening on http://0.0.0.0:${config.PORT} (${config.NODE_ENV})`);
  });
}

module.exports = app;

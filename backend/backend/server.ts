import express from 'express';
import http from 'http';
import { Server } from 'socket.io';
import cors from 'cors';
import dotenv from 'dotenv';
import { connectDatabase, disconnectDatabase } from './config/database';
import { setupSocketHandler } from './sockets/socketHandler';

// Import Routes
import authRoutes from './routes/auth';
import userRoutes from './routes/users';
import postRoutes from './routes/posts';
import chatRoutes from './routes/chats';

// Seed Initial Data check
import { seedDatabaseIfEmpty } from './seed';

dotenv.config();

const app = express();
const server = http.createServer(app);

// Initialize Socket.io
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE"]
  }
});

const PORT = process.env.PORT || 4000;

// Apply Middlewares
app.use(cors());
app.use(express.json());

// Main Health Endpoint for Android Connection verification
app.get('/api/health', (req, res) => {
  res.status(200).json({ 
    status: "ok", 
    message: "Empire network hub online and integrated." 
  });
});

// Configure Custom Routes
import { verifyToken } from './controllers/authController';

app.use('/api/auth', authRoutes);
app.use('/api/users', verifyToken, userRoutes);
app.use('/api/posts', verifyToken, postRoutes);
app.use('/api/chat', verifyToken, chatRoutes);

// Socket Handler setup
setupSocketHandler(io);

// Global Error Handler Middleware
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error("⚠️ Global Engine Error caught:", err);
  res.status(500).json({ error: "Internal server error", details: err.message });
});

// Initialize and Start Server
const startServer = async () => {
  try {
    // 1. Core database lock
    await connectDatabase();
    
    // 2. Perform seed checks to initialize dummy avatars
    await seedDatabaseIfEmpty();

    // 3. Start listener
    server.listen(PORT, () => {
      console.log(`===============================================`);
      console.log(`📡 One Earth API Server is running on port: ${PORT}`);
      console.log(`🟢 Health Check: http://localhost:${PORT}/api/health`);
      console.log(`===============================================`);
    });
  } catch (error) {
    console.error("❌ Critical server bootstrap failed:", error);
    process.exit(1);
  }
};

startServer();

// Handle graceful shutdowns
process.on('SIGINT', async () => {
  console.log('\n🛑 SIGINT signal received.');
  await disconnectDatabase();
  server.close(() => {
    console.log('⛔ HTTP Server closed safely.');
    process.exit(0);
  });
});

process.on('SIGTERM', async () => {
  console.log('\n🛑 SIGTERM signal received.');
  await disconnectDatabase();
  server.close(() => {
    console.log('⛔ HTTP Server closed safely.');
    process.exit(0);
  });
});

export default app;


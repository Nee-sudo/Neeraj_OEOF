import { Router } from 'express';
import { 
  getRooms, 
  createRoom, 
  getMessages, 
  getReceipts,
  sendMessage, 
  swapOrActivateRoom, 
  archiveRoom,
  deleteRoom,
  deleteMessage
} from '../controllers/chatController';

const router = Router();

router.get('/rooms', getRooms);
router.post('/rooms', createRoom);
router.get('/rooms/:roomId/messages', getMessages);
router.get('/rooms/:roomId/receipts', getReceipts);
router.post('/rooms/:roomId/messages', sendMessage);
router.delete('/rooms/:roomId', deleteRoom);
router.delete('/rooms/:roomId/messages/:messageId', deleteMessage);


// Connection state triggers
router.post('/rooms/:roomId/swap', swapOrActivateRoom);
router.post('/rooms/:roomId/archive', archiveRoom);

export default router;

import { Router } from 'express';
import { 
  getRooms, 
  createRoom, 
  getMessages, 
  sendMessage, 
  swapOrActivateRoom, 
  archiveRoom,
  deleteRoom
} from '../controllers/chatController';

const router = Router();

router.get('/rooms', getRooms);
router.post('/rooms', createRoom);
router.get('/rooms/:roomId/messages', getMessages);
router.post('/rooms/:roomId/messages', sendMessage);
router.delete('/rooms/:roomId', deleteRoom);


// Connection state triggers
router.post('/rooms/:roomId/swap', swapOrActivateRoom);
router.post('/rooms/:roomId/archive', archiveRoom);

export default router;

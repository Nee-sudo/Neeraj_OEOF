import { Router } from 'express';
import { getUserProfile, updateUserProfile, getAllUsers, visitUserProfile, sendFriendRequest } from '../controllers/authController';

const router = Router();

router.get('/', getAllUsers);
router.get('/:userId', getUserProfile);
router.put('/:userId', updateUserProfile);
router.post('/:userId/visit', visitUserProfile);
router.post('/:userId/friend-request', sendFriendRequest);

export default router;

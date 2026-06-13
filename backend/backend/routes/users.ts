import { Router } from 'express';
import { getUserProfile, updateUserProfile, getAllUsers } from '../controllers/authController';

const router = Router();

router.get('/', getAllUsers);
router.get('/:userId', getUserProfile);
router.put('/:userId', updateUserProfile);

export default router;

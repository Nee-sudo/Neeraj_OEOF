import { Router } from 'express';
import { getNotifications, markAsRead, markAllNotificationsAsRead } from '../controllers/notificationController';

const router = Router();

router.get('/', getNotifications);
router.put('/:id/read', markAsRead);
router.post('/read-all', markAllNotificationsAsRead);

export default router;

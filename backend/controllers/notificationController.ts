import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { INotification } from '../models/Notification';

// Helper to generate a unique notification ID
const generateUniqueId = (): string => {
  return `${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
};

// Helper utility to create a notification in any controller
export const createNotificationDirectly = async (
  recipientId: string,
  senderId: string,
  type: string,
  title: string,
  body: string
) => {
  try {
    const db = getFirestoreDb();

    // Normalize identifiers before comparison
    const normalizeId = (id: string | any): string => {
      if (!id) return '';
      return String(id).toLowerCase().trim().replace(/^@/, '');
    };

    const normalizedRecipient = normalizeId(recipientId);
    const normalizedSender = normalizeId(senderId);

    // MANDATORY SELF-NOTIFICATION BLOCK
    if (normalizedRecipient === normalizedSender || !normalizedRecipient || !normalizedSender) {
      console.log(`[SELF-NOTIFICATION BLOCKED] Recipient: ${normalizedRecipient}, Sender: ${normalizedSender}`);
      return null;
    }

    const cleanRecipient = normalizedRecipient;
    const cleanSender = normalizedSender;

    const notifId = generateUniqueId();
    const notification: INotification = {
      id: notifId,
      recipientId: cleanRecipient,
      senderId: cleanSender,
      type,
      title,
      body,
      isRead: false,
      createdAt: Date.now()
    };

    await db.collection('notifications').doc(notifId).set(notification);

    // Direct in-app real-time WebSocket delivery (Instagram-style) (circular-safe require)
    try {
      const { sendRealtimeNotification } = require('../sockets/socketHandler');
      sendRealtimeNotification(cleanRecipient, notification);
    } catch (wsError) {
      console.error("[WEBSOCKET REAL-TIME DELIVERY FAIL]:", wsError);
    }

    console.log(`
[VERBOSE DEBUG NOTIFICATION LOG]
- Notification ID: ${notifId}
- Notification Type: ${type}
- Actor ID (Sender): ${cleanSender}
- Recipient ID: ${cleanRecipient}
- Creation Status: SUCCESS
- Save Status: PERSISTED_IN_FIRESTORE
- Delivery Status: QUEUED_FOR_RETRIEVAL
    `);
    return notification;
  } catch (error: any) {
    console.error("Error creating notification:", error);
    return null;
  }
};

// GET /api/notifications
export const getNotifications = async (req: any, res: Response) => {
  try {
    const db = getFirestoreDb();
    const myId = String(req.userId).toLowerCase().trim();

    const snapshot = await db.collection('notifications').where('recipientId', '==', myId).get();
    const notifications: INotification[] = snapshot.docs.map((doc: any) => doc.data() as INotification);

    // Sort newest first
    notifications.sort((a, b) => b.createdAt - a.createdAt);

    res.status(200).json(notifications);
  } catch (error: any) {
    console.error("Error fetching notifications:", error);
    res.status(500).json({ error: error.message });
  }
};

// PUT /api/notifications/:id/read
export const markAsRead = async (req: any, res: Response) => {
  try {
    const { id } = req.params;
    const db = getFirestoreDb();
    const myId = String(req.userId).toLowerCase().trim();

    const docRef = db.collection('notifications').doc(String(id));
    const notifDoc = await docRef.get();

    if (!notifDoc.exists) {
      res.status(404).json({ error: "Notification not found" });
      return;
    }

    const notification = notifDoc.data() as INotification;

    // Security Check: Notifications belong only to recipient
    if (notification.recipientId !== myId) {
      res.status(403).json({ error: "Access denied. This notification does not belong to you." });
      return;
    }

    notification.isRead = true;
    await docRef.set(notification);

    res.status(200).json(notification);
  } catch (error: any) {
    console.error("Error marking notification as read:", error);
    res.status(500).json({ error: error.message });
  }
};

// POST /api/notifications/read-all
export const markAllNotificationsAsRead = async (req: any, res: Response) => {
  try {
    const db = getFirestoreDb();
    const myId = String(req.userId).toLowerCase().trim();

    const snapshot = await db.collection('notifications').where('recipientId', '==', myId).get();
    const batchPromises = snapshot.docs.map(async (docRefObj: any) => {
      const data = docRefObj.data();
      if (!data.isRead) {
        data.isRead = true;
        await docRefObj.ref.set(data);
      }
    });

    await Promise.all(batchPromises);

    res.status(200).json({ success: true, message: "All notifications marked as read." });
  } catch (error: any) {
    console.error("Error marking all notifications as read:", error);
    res.status(500).json({ error: error.message });
  }
};

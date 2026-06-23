import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { INotification } from '../models/Notification';
import * as admin from 'firebase-admin';

let hasValidFirebaseCredential = false;

// Robust JSON deserializer for Firebase Service Account environment strings
const parseServiceAccount = (envVal: string): any => {
  let val = envVal.trim();
  
  // Clean outer quotes if wrapping the stringified JSON
  if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
    val = val.slice(1, -1).trim();
  }

  // Detect and resolve Base64 encoded JSON
  if (!val.startsWith('{')) {
    try {
      const decoded = Buffer.from(val, 'base64').toString('utf8');
      if (decoded.trim().startsWith('{')) {
        val = decoded.trim();
        console.log("🔓 [FCM INIT] Successfully decoded Base64 FIREBASE_SERVICE_ACCOUNT!");
      }
    } catch (_) {
      // Ignore decoded error and proceed to standard parsing
    }
  }
  
  try {
    return JSON.parse(val);
  } catch (err1: any) {
    // Attempt to salvage/unescape internal escaped quotes and newlines
    try {
      const salvaged = val
        .replace(/\\"/g, '"')
        .replace(/\\\\n/g, '\n')
        .replace(/\\n/g, '\n');
      return JSON.parse(salvaged);
    } catch (err2: any) {
      throw new Error(`Standard JSON parse error: ${err1.message}. Hand-salvaged parse error: ${err2.message}`);
    }
  }
};

try {
  const serviceAccountEnv = process.env.FIREBASE_SERVICE_ACCOUNT;
  const hasServiceAccountEnv = serviceAccountEnv && serviceAccountEnv !== 'true' && serviceAccountEnv !== 'false' && serviceAccountEnv.trim().length > 1;

  const clientEmailEnv = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKeyEnv = process.env.FIREBASE_PRIVATE_KEY;
  const hasIndividualEnv = !!(clientEmailEnv && privateKeyEnv);

  if (hasServiceAccountEnv || hasIndividualEnv) {
    let serviceAccount: any = null;

    if (hasServiceAccountEnv) {
      const cleanVal = serviceAccountEnv!.trim();
      const startChar = cleanVal.substring(0, 50).replace(/[\n\r]/g, ' ');
      const endChar = cleanVal.substring(Math.max(0, cleanVal.length - 50)).replace(/[\n\r]/g, ' ');
      console.log(`🔍 [FCM DIAGNOSTIC] Found FIREBASE_SERVICE_ACCOUNT in environment.`);
      console.log(`🔍 [FCM DIAGNOSTIC] Length: ${serviceAccountEnv!.length} chars. Start: "${startChar}...", End: "...${endChar}"`);

      try {
        serviceAccount = parseServiceAccount(serviceAccountEnv!);
      } catch (err: any) {
        console.error(`❌ [FCM INIT FAILURE] Failed to parse FIREBASE_SERVICE_ACCOUNT JSON: ${err.message}`);
      }
    }

    if (!serviceAccount && hasIndividualEnv) {
      console.log(`🔍 [FCM DIAGNOSTIC] Reconstructing credentials from individual environment variables (FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY).`);
      serviceAccount = {
        projectId: process.env.FIREBASE_PROJECT_ID || 'oneearthonefamily',
        clientEmail: clientEmailEnv!.trim(),
        privateKey: privateKeyEnv!.trim().replace(/\\n/g, '\n')
      };
    }

    if (serviceAccount) {
      if (admin.apps.length === 0) {
        admin.initializeApp({
          credential: admin.credential.cert(serviceAccount)
        });
      }
      hasValidFirebaseCredential = true;
      console.log("🔥 [FCM INIT] Firebase Admin SDK initialized successfully.");
    } else {
      console.error("❌ [FCM INIT FAILURE] Could not configure Firebase credentials. Either JSON string was invalid or individual variables are incomplete.");
      console.warn("👉 Action Required: Ensure 'FIREBASE_SERVICE_ACCOUNT' has valid content, OR configure 'FIREBASE_CLIENT_EMAIL' and 'FIREBASE_PRIVATE_KEY' as separate flat settings.");
      
      // Fallback default init to prevent server boot failure
      if (admin.apps.length === 0) {
        admin.initializeApp();
      }
      hasValidFirebaseCredential = false;
    }
  } else {
    // No Service Account provided, or literally set to boolean flag 'true'/'false'
    if (serviceAccountEnv === 'true' || serviceAccountEnv === 'false' || (serviceAccountEnv && serviceAccountEnv.trim().length <= 1)) {
      console.warn(`⚠️ [FCM INIT WARNING] FIREBASE_SERVICE_ACCOUNT is invalid or too short in environment (length: ${serviceAccountEnv?.length || 0}).`);
    } else {
      console.log("ℹ️ [FCM INIT] No FIREBASE_SERVICE_ACCOUNT or individual keys configured in environment. Skipping real-time push dispatches.");
    }
    
    if (admin.apps.length === 0) {
      admin.initializeApp({
        projectId: process.env.FIREBASE_PROJECT_ID || 'oneearthonefamily'
      });
    }
    hasValidFirebaseCredential = false;
  }
} catch (e: any) {
  console.warn("⚠️ [FCM INIT] Firebase Admin SDK initialization fallback error:", e.message);
  hasValidFirebaseCredential = false;
}

// Helper to generate a unique notification ID
const generateUniqueId = (): string => {
  return `${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
};

// Function targeting production-ready FCM push notifications delivery
export const sendFcmNotification = async (recipientId: string, notification: INotification) => {
  try {
    const cleanRecipientId = String(recipientId).toLowerCase().trim().replace(/^@/, '');

    if (!hasValidFirebaseCredential) {
      console.warn(`[FCM SKIP] Skipping push notification dispatch to ${cleanRecipientId} (Firebase Service Account not configured).`);
      return;
    }

    const db = getFirestoreDb();
    
    // 1. Recipient security / validation check
    const userDoc = await db.collection('users').doc(cleanRecipientId).get();
    if (!userDoc.exists) {
      console.log(`[FCM SKIP] Recipient profile ${cleanRecipientId} does not exist. Skipping push.`);
      return;
    }
    
    const user = userDoc.data();
    const token = user?.fcmToken;
    if (!token) {
      console.log(`[FCM SKIP] Recipient ${cleanRecipientId} has no registered FCM Token. Push skipped.`);
      return;
    }
    
    console.log(`[FCM PROGRESS] Dispatching push notification to ${cleanRecipientId} (Token: ${token.substring(0, 15)}...)`);
    
    // 2. Pre-calculate Unread Notification Count for Badge accuracies
    let badgeCount = 1;
    try {
      const snapshot = await db.collection('notifications').where('recipientId', '==', cleanRecipientId).get();
      if (snapshot && snapshot.docs) {
        badgeCount = snapshot.docs.filter((doc: any) => !doc.data().isRead).length;
      }
    } catch (badgeErr) {
      console.error("[FCM BADGE] Counting failed:", badgeErr);
    }
    
    // 3. Formulate highly specified payload with message properties
    const message: admin.messaging.Message = {
      token: token,
      notification: {
        title: notification.title,
        body: notification.body
      },
      data: {
        deep_link_type: String(notification.type),
        notif_id: String(notification.id),
        title: String(notification.title),
        body: String(notification.body),
        room_id: notification.roomId !== undefined && notification.roomId !== null ? String(notification.roomId) : "",
        post_id: notification.postId !== undefined && notification.postId !== null ? String(notification.postId) : "",
        user_id: notification.userId !== undefined && notification.userId !== null ? String(notification.userId) : "",
        badge_count: String(badgeCount)
      },
      android: {
        priority: "high",
        notification: {
          channelId: "high_importance_channel",
          sound: "default",
          notificationCount: badgeCount
        }
      },
      apns: {
        payload: {
          aps: {
            badge: badgeCount,
            sound: "default"
          }
        }
      }
    };
    
    const response = await admin.messaging().send(message);
    console.log(`[FCM SUCCESS] Target delivered successfully. Message ID: ${response}`);
  } catch (error: any) {
    if (error.message.includes("metadata.google.internal") || error.message.includes("Failed to determine project ID") || error.message.includes("credential")) {
      console.warn("⚠️ [FCM DISPATCH SKIP]: Firebase Service Account credentials are missing or could not be verified automatically via GCP Metadata.");
      console.warn("👉 Action Required: To enable real push notifications, please download the Service Account Key JSON from the Firebase Console, and set it as the 'FIREBASE_SERVICE_ACCOUNT' environment variable in your AI Studio Secrets panel.");
    } else {
      console.error("[FCM DISPATCH FAILURE]:", error.message);
    }
  }
};

// Helper utility to create a notification in any controller
export const createNotificationDirectly = async (
  recipientId: string,
  senderId: string,
  type: string,
  title: string,
  body: string,
  roomId?: number,
  postId?: number,
  userId?: string
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

    // Suppress message notifications if recipient is actively viewing the room
    if (type === 'message' && roomId !== undefined) {
      try {
        const { getActiveRoomForUser } = require('../sockets/socketHandler');
        const activeRoom = getActiveRoomForUser(cleanRecipient);
        if (activeRoom !== null && activeRoom === Number(roomId)) {
          console.log(`[NOTIFICATION SUPPRESSED] Recipient: ${cleanRecipient} is actively viewing room ${roomId}`);
          return null;
        }
      } catch (err) {
        console.error("Error checking active room for user:", err);
      }
    }

    const notifId = generateUniqueId();
    const notification: INotification = {
      id: notifId,
      recipientId: cleanRecipient,
      senderId: cleanSender,
      type,
      title,
      body,
      isRead: false,
      createdAt: Date.now(),
      roomId,
      postId,
      userId
    };

    await db.collection('notifications').doc(notifId).set(notification);

    // Direct in-app real-time WebSocket delivery (Instagram-style) (circular-safe require)
    try {
      const { sendRealtimeNotification } = require('../sockets/socketHandler');
      sendRealtimeNotification(cleanRecipient, notification);
    } catch (wsError) {
      console.error("[WEBSOCKET REAL-TIME DELIVERY FAIL]:", wsError);
    }

    // Direct FCM Push integration
    try {
      await sendFcmNotification(cleanRecipient, notification);
    } catch (fcmErr) {
      console.error("[FCM DISPATCH ENCOUNTERED ERROR]:", fcmErr);
    }

    console.log(`
[VERBOSE DEBUG NOTIFICATION LOG]
- Notification ID: ${notifId}
- Notification Type: ${type}
- Actor ID (Sender): ${cleanSender}
- Recipient ID: ${cleanRecipient}
- Creation Status: SUCCESS
- Save Status: PERSISTED_IN_FIRESTORE
- Delivery Status: FCM_AND_WEBSOCKET_QUEUED
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

import { Server, Socket } from 'socket.io';
import { getFirestoreDb } from '../config/database';
import { IChatMessage } from '../models/ChatMessage';
import { IChatRoom } from '../models/ChatRoom';
import { getNextSequenceValue } from '../models/Counter';
import { createNotificationDirectly } from '../controllers/notificationController';
import { resolveRecipientId } from '../controllers/chatController';

let ioInstance: Server | null = null;

export const sendRealtimeNotification = (recipientId: string, notification: any) => {
  if (ioInstance) {
    const userChannel = `user_${recipientId.toLowerCase().trim()}`;
    ioInstance.to(userChannel).emit('new_notification', notification);
    console.log(`🔌 WebSockets: Dispatched real-time notification to ${userChannel}`, notification);
  } else {
    console.log(`🔌 WebSockets: Cannot send real-time notification, ioInstance is null.`);
  }
};

export const setupSocketHandler = (io: Server) => {
  ioInstance = io;
  io.on('connection', (socket: Socket) => {
    console.log(`🔌 WebSockets: Client connected (${socket.id})`);

    // Listen to subscribe to user personal notification channel
    socket.on('join_user', (data: { userId: string }) => {
      const userChannel = `user_${String(data.userId).toLowerCase().trim()}`;
      socket.join(userChannel);
      console.log(`🔌 WebSockets: Client ${socket.id} joined personal channel ${userChannel}`);
    });

    socket.on('leave_user', (data: { userId: string }) => {
      const userChannel = `user_${String(data.userId).toLowerCase().trim()}`;
      socket.leave(userChannel);
      console.log(`🔌 WebSockets: Client ${socket.id} left personal channel ${userChannel}`);
    });

    // Listen to subscribe to a chat room channel
    socket.on('join_room', (data: { roomId: string | number }) => {
      const roomChannel = `room_${data.roomId}`;
      socket.join(roomChannel);
      console.log(`💬 WebSockets: Client ${socket.id} joined channel ${roomChannel}`);
    });

    // Real-time chat message exchange
    socket.on('send_message', async (data: { 
      roomId: number; 
      senderId: string; 
      senderName: string; 
      messageText: string; 
    }) => {
      try {
        const { roomId, senderId, senderName, messageText } = data;
        const db = getFirestoreDb();
        
        // Save to Firestore
        const nextId = await getNextSequenceValue('message_id');
        const newMessage: IChatMessage = {
          id: nextId,
          roomId: Number(roomId),
          senderId,
          senderName,
          messageText,
          timestamp: Date.now()
        };
        
        await db.collection('chatMessages').doc(String(nextId)).set(newMessage);

        // Notify recipient of message
        try {
          const recipientId = await resolveRecipientId(db, Number(roomId), senderName);
          if (recipientId) {
            await createNotificationDirectly(
              recipientId,
              senderId,
              "message",
              `New message from ${senderName}`,
              messageText
            );
          }
        } catch (notifErr) {
          console.error("Websocket message notification failure:", notifErr);
        }

        // Update ChatRoom
        const roomRef = db.collection('chatRooms').doc(String(roomId));
        const roomSnapshot = await roomRef.get();
        if (roomSnapshot.exists) {
          const room = roomSnapshot.data() as IChatRoom;
          room.lastMessage = messageText;
          room.lastMessageTime = Date.now();
          await roomRef.set(room);
        } else {
          // Fallback locate and save
          const query = await db.collection('chatRooms').where('id', '==', Number(roomId)).get();
          if (!query.empty) {
            const doc = query.docs[0];
            const room = doc.data() as IChatRoom;
            room.lastMessage = messageText;
            room.lastMessageTime = Date.now();
            await doc.ref.set(room);
          }
        }

        // Broadcast to dynamic channel
        const roomChannel = `room_${roomId}`;
        io.to(roomChannel).emit('new_message', newMessage);
        
        console.log(`✉️ WebSockets: Message broadcasted in ${roomChannel}`);
      } catch (error) {
        console.error("WebSockets error on send_message:", error);
      }
    });

    // Listen for message delivered receipts
    socket.on('message_delivered', async (data: { roomId: number; timestamp: number; senderName: string }) => {
      try {
        const { roomId, timestamp, senderName } = data;
        const roomChannel = `room_${roomId}`;
        io.to(roomChannel).emit('message_delivered', { roomId, timestamp, senderName });
        console.log(`🚚 WebSockets: Message delivered receipt in ${roomChannel} for ${senderName}`);

        // Persist delivered receipt to Firestore separately under doc: {roomId}_{senderName}_delivered
        const db = getFirestoreDb();
        const receiptId = `${roomId}_${senderName}_delivered`;
        await db.collection('chatReceipts').doc(receiptId).set({
          id: receiptId,
          roomId: Number(roomId),
          senderName,
          timestamp: Number(timestamp),
          type: 'delivered'
        });
      } catch (error) {
        console.error("WebSockets error on message_delivered:", error);
      }
    });

    // Listen for message read receipts
    socket.on('message_read', async (data: { roomId: number; timestamp: number; senderName: string }) => {
      try {
        const { roomId, timestamp, senderName } = data;
        const roomChannel = `room_${roomId}`;
        io.to(roomChannel).emit('message_read', { roomId, timestamp, senderName });
        console.log(`📖 WebSockets: Message read receipt in ${roomChannel} for ${senderName}`);

        // Persist read receipt to Firestore separately under doc: {roomId}_{senderName}_read
        const db = getFirestoreDb();
        const receiptId = `${roomId}_${senderName}_read`;
        await db.collection('chatReceipts').doc(receiptId).set({
          id: receiptId,
          roomId: Number(roomId),
          senderName,
          timestamp: Number(timestamp),
          type: 'read'
        });
      } catch (error) {
        console.error("WebSockets error on message_read:", error);
      }
    });

    // Broadcast feed update when a new post or reaction occurs
    socket.on('post_broadcast', (data: { type: string; postId?: number }) => {
      io.emit('feed_updated', data);
      console.log(`📢 WebSockets: Broadcasted feed update event [${data.type}]`);
    });

    socket.on('disconnect', () => {
      console.log(`🔌 WebSockets: Client disconnected (${socket.id})`);
    });
  });
};

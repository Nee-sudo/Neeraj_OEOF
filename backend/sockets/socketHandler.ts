import { Server, Socket } from 'socket.io';
import { getFirestoreDb } from '../config/database';
import { IChatMessage } from '../models/ChatMessage';
import { IChatRoom } from '../models/ChatRoom';
import { getNextSequenceValue } from '../models/Counter';

export const setupSocketHandler = (io: Server) => {
  io.on('connection', (socket: Socket) => {
    console.log(`🔌 WebSockets: Client connected (${socket.id})`);

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

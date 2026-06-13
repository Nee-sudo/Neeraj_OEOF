import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IChatRoom } from '../models/ChatRoom';
import { IChatMessage } from '../models/ChatMessage';
import { getNextSequenceValue } from '../models/Counter';

export const getRooms = async (req: Request, res: Response) => {
  try {
    const db = getFirestoreDb();
    const snapshot = await db.collection('chatRooms').get();
    const rooms = snapshot.docs.map(doc => doc.data() as IChatRoom);
    
    // Sort in memory to guarantee no missing index runtime error
    rooms.sort((a, b) => b.lastMessageTime - a.lastMessageTime);

    // Apply filtering by participant name to ensure conversations are only visible to the participants
    let filteredRooms = rooms;
    if (req.query.name) {
      const searchName = String(req.query.name).toLowerCase().trim();
      filteredRooms = rooms.filter(r => {
        if (!r.participantName) return false;
        const parts = r.participantName.split(" | ");
        return parts.some(part => part.toLowerCase().trim() === searchName);
      });
    }

    res.status(200).json(filteredRooms);
  } catch (error: any) {
    console.error("Firestore getRooms Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const createRoom = async (req: Request, res: Response) => {
  try {
    const roomData = req.body;
    const db = getFirestoreDb();
    
    const participantName = roomData.participantName ? String(roomData.participantName).trim() : '';
    
    // Scan matching existing chats to prevent duplicates
    const snapshot = await db.collection('chatRooms').get();
    const allRooms = snapshot.docs.map(doc => doc.data() as IChatRoom);
    
    const existing = allRooms.find(r => 
      r.participantName.toLowerCase() === participantName.toLowerCase()
    );

    if (existing) {
       res.status(200).json(existing);
       return;
    }

    // Determine if we can immediately trigger active focus slot
    const activeCount = allRooms.filter(r => r.isActive).length;
    const makeActive = activeCount < 3;

    const nextId = await getNextSequenceValue('room_id');
    const newRoom: IChatRoom = {
      id: nextId,
      roomName: roomData.roomName || "Dialogue",
      participantName: participantName || "Anonymous Leader",
      participantFlag: roomData.participantFlag || "🌍",
      participantRank: roomData.participantRank || "Citizen",
      participantTerritory: roomData.participantTerritory || "Global",
      lastMessage: roomData.lastMessage || "",
      lastMessageTime: Date.now(),
      isActive: makeActive,
      isWaiting: !makeActive
    };

    await db.collection('chatRooms').doc(String(nextId)).set(newRoom);
    res.status(201).json(newRoom);
  } catch (error: any) {
    console.error("Firestore createRoom Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const getMessages = async (req: Request, res: Response) => {
  try {
    const { roomId } = req.params;
    const db = getFirestoreDb();
    
    const snapshot = await db.collection('chatMessages').where('roomId', '==', Number(roomId)).get();
    const messages = snapshot.docs.map(doc => doc.data() as IChatMessage);
    
    // Sort chronologically in memory safely
    messages.sort((a, b) => a.timestamp - b.timestamp);
    res.status(200).json(messages);
  } catch (error: any) {
    console.error("Firestore getMessages Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const sendMessage = async (req: Request, res: Response) => {
  try {
    const { roomId } = req.params;
    const messageData = req.body;
    const db = getFirestoreDb();

    const nextId = await getNextSequenceValue('message_id');
    const newMessage: IChatMessage = {
      id: nextId,
      roomId: Number(roomId),
      senderId: messageData.senderId || "other",
      senderName: messageData.senderName || "Sender",
      messageText: messageData.messageText || "",
      timestamp: Date.now()
    };

    await db.collection('chatMessages').doc(String(nextId)).set(newMessage);

    // Update parent ChatRoom's preview metadata as well
    const roomRef = db.collection('chatRooms').doc(String(roomId));
    const roomSnapshot = await roomRef.get();
    
    if (roomSnapshot.exists) {
      const room = roomSnapshot.data() as IChatRoom;
      room.lastMessage = messageData.messageText || "";
      room.lastMessageTime = Date.now();
      await roomRef.set(room);
    } else {
      // Look up room using where clause just in case
      const query = await db.collection('chatRooms').where('id', '==', Number(roomId)).get();
      if (!query.empty) {
        const doc = query.docs[0];
        const room = doc.data() as IChatRoom;
        room.lastMessage = messageData.messageText || "";
        room.lastMessageTime = Date.now();
        await doc.ref.set(room);
      }
    }

    res.status(201).json(newMessage);
  } catch (error: any) {
    console.error("Firestore sendMessage Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const swapOrActivateRoom = async (req: Request, res: Response) => {
  try {
    const { roomId } = req.params;
    const db = getFirestoreDb();
    
    const roomRef = db.collection('chatRooms').doc(String(roomId));
    let roomSnap = await roomRef.get();
    
    let docRef = roomRef;
    if (!roomSnap.exists) {
      const query = await db.collection('chatRooms').where('id', '==', Number(roomId)).get();
      if (query.empty) {
        res.status(404).json({ error: "Room not found." });
        return;
      }
      roomSnap = query.docs[0];
      docRef = query.docs[0].ref;
    }

    const room = roomSnap.data() as IChatRoom;
    if (room.isActive) {
       res.status(200).json(room);
       return;
    }

    // Verify online terminal limit count
    const snapshot = await db.collection('chatRooms').get();
    const allRooms = snapshot.docs.map(doc => doc.data() as IChatRoom);
    
    const activeCount = allRooms.filter(r => r.isActive).length;
    if (activeCount >= 3) {
       res.status(400).json({ error: "Active dialogue slots are full. Please swap index terminals." });
       return;
    }

    room.isActive = true;
    room.isWaiting = false;
    await docRef.set(room);

    res.status(200).json(room);
  } catch (error: any) {
    console.error("Firestore swapOrActivateRoom Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const archiveRoom = async (req: Request, res: Response) => {
  try {
    const { roomId } = req.params;
    const db = getFirestoreDb();
    
    const roomRef = db.collection('chatRooms').doc(String(roomId));
    let roomSnap = await roomRef.get();
    
    let docRef = roomRef;
    if (!roomSnap.exists) {
      const query = await db.collection('chatRooms').where('id', '==', Number(roomId)).get();
      if (query.empty) {
        res.status(404).json({ error: "Room not found." });
        return;
      }
      roomSnap = query.docs[0];
      docRef = query.docs[0].ref;
    }

    const room = roomSnap.data() as IChatRoom;
    room.isActive = false;
    room.isWaiting = true;
    await docRef.set(room);

    res.status(200).json(room);
  } catch (error: any) {
    console.error("Firestore archiveRoom Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const deleteRoom = async (req: Request, res: Response) => {
  try {
    const { roomId } = req.params;
    const db = getFirestoreDb();
    
    // 1. Delete matching messages
    const messagesQuery = await db.collection('chatMessages').where('roomId', '==', Number(roomId)).get();
    for (const doc of messagesQuery.docs) {
      await doc.ref.delete();
    }

    // 2. Delete the room document
    const roomRef = db.collection('chatRooms').doc(String(roomId));
    const roomSnap = await roomRef.get();
    if (roomSnap.exists) {
      await roomRef.delete();
    } else {
      // Look up room using where clause just in case
      const query = await db.collection('chatRooms').where('id', '==', Number(roomId)).get();
      if (!query.empty) {
        await query.docs[0].ref.delete();
      }
    }

    res.status(200).json({ success: true });
  } catch (error: any) {
    console.error("Firestore deleteRoom Error:", error);
    res.status(500).json({ error: error.message });
  }
};


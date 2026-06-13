import { getFirestoreDb } from './config/database';
import { IUser } from './models/User';
import { IPost } from './models/Post';
import { IComment } from './models/Comment';

export const seedDatabaseIfEmpty = async () => {
  try {
    const db = getFirestoreDb();

    // 1. Seed Users (Kept empty for a fully clean start)
    const usersSnapshot = await db.collection('users').limit(1).get();
    if (usersSnapshot.empty) {
      console.log('🌱 Firestore: No default administrative citizens seeded.');
    }

    // 2. Initialize Sequence Counters
    const countersSnapshot = await db.collection('counters').limit(1).get();
    if (countersSnapshot.empty) {
      console.log('🌱 Firestore: Seeding incremental sequence counters...');
      await db.collection('counters').doc('post_id').set({ seq: 0 });
      await db.collection('counters').doc('comment_id').set({ seq: 0 });
      await db.collection('counters').doc('room_id').set({ seq: 0 });
      await db.collection('counters').doc('message_id').set({ seq: 0 });
      console.log('✅ Firestore: Sequence tables successfully synchronized.');
    }

    // 3. Seed Posts (Kept empty for a fully clean start)
    const postsSnapshot = await db.collection('posts').limit(1).get();
    if (postsSnapshot.empty) {
      console.log('🌱 Firestore: No default feeds seeded.');
    }

    // 4. Seed Comments (Kept empty)
    const commentsSnapshot = await db.collection('comments').limit(1).get();
    if (commentsSnapshot.empty) {
      console.log('🌱 Firestore: No default comments seeded.');
    }

  } catch (error) {
    console.error('⚠️ Firestore Seeding check failed:', error);
  }
};

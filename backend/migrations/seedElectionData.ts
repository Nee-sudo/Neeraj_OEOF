import { getFirestoreDb } from '../config/database';
import { IElection, ElectionState } from '../models/Election';
import crypto from 'crypto';

export async function seedElectionData(): Promise<void> {
  const db = getFirestoreDb();
  
  try {
    const electionsSnapshot = await db.collection('elections').limit(1).get();
    if (!electionsSnapshot.empty) {
      console.log('📋 Elections already seeded, skipping');
      return;
    }
    
    console.log('🌱 Seeding initial election data...');
    
    const now = Date.now();
    const oneDay = 24 * 60 * 60 * 1000;
    
    const sampleElection: IElection = {
      electionId: `election_${crypto.randomUUID()}`,
      type: 'King',
      state: ElectionState.NOMINATION_OPEN,
      nominationOpensAt: now,
      nominationClosesAt: now + 7 * oneDay,
      votingOpensAt: now + 7 * oneDay,
      votingClosesAt: now + 14 * oneDay,
      scoringFormulaVersion: 'v1',
      voteSaturationCeiling: 100,
      createdAt: now,
      createdBy: 'SYSTEM',
      updatedAt: now
    };
    
    await db.collection('elections').doc(sampleElection.electionId).set(sampleElection);
    console.log('✅ Sample election seeded successfully');
  } catch (error) {
    console.error('❌ Seeding failed:', error);
  }
}

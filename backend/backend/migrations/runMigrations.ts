import { connectDatabase, getFirestoreDb } from '../config/database';
import { createElectionCollections, rollbackElectionCollections } from './001_create_election_collections';
import { seedElectionData } from './seedElectionData';

export async function runMigrations(): Promise<void> {
  try {
    const db = getFirestoreDb();
    console.log('📋 Running database migrations...');
    
    // Migration 001: Create election collections and indexes
    await createElectionCollections(db);
    
    // Migration 002: Seed initial sample data
    await seedElectionData();
    
    console.log('✅ All migrations completed successfully');
  } catch (error) {
    console.error('❌ Migration failed:', error);
    throw error;
  }
}

export { rollbackElectionCollections };

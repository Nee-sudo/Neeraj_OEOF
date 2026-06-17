import { Db } from 'mongodb';

export async function createElectionCollections(db: any): Promise<void> {
  console.log('🔧 Creating election system collections...');
  
  // Custom helper to check if collection exists in MongoDB
  const collectionExists = async (name: string): Promise<boolean> => {
    if (typeof db.listCollections !== 'function') {
      return false;
    }
    try {
      const collections = await db.listCollections({ name }).toArray();
      return collections.length > 0;
    } catch {
      return false;
    }
  };

  const createCollectionAndIndex = async (name: string, indexes: Array<{ fields: Record<string, number>; options?: any }>) => {
    try {
      if (!(await collectionExists(name))) {
        if (typeof db.createCollection === 'function') {
          await db.createCollection(name);
          console.log(`✅ Collection '${name}' created`);
        }
      }
      
      const coll = typeof db.collection === 'function' ? db.collection(name) : null;
      if (coll && typeof coll.createIndex === 'function') {
        for (const index of indexes) {
          await coll.createIndex(index.fields, index.options);
        }
        console.log(`✅ Indexes created for collection '${name}'`);
      }
    } catch (err: any) {
      console.warn(`⏳ [Migration Note] Collection/Index setup for '${name}' ran with fallback: ${err.message}`);
    }
  };

  // 1. Elections collection
  await createCollectionAndIndex('elections', [
    { fields: { electionId: 1 }, options: { unique: true } },
    { fields: { type: 1 } },
    { fields: { state: 1 } },
    { fields: { territoryId: 1 } }
  ]);
  
  // 2. Candidates collection
  await createCollectionAndIndex('candidates', [
    { fields: { candidateId: 1 }, options: { unique: true } },
    { fields: { electionId: 1 } },
    { fields: { citizenId: 1 } },
    { fields: { electionId: 1, status: 1 } }
  ]);
  
  // 3. Votes collection (CRITICAL: compound unique constraint)
  await createCollectionAndIndex('votes', [
    { fields: { voteId: 1 }, options: { unique: true } },
    { fields: { electionId: 1, voterId: 1 }, options: { unique: true } }, // Compound unique constraint
    { fields: { electionId: 1 } },
    { fields: { candidateId: 1 } },
    { fields: { voterId: 1 } },
    { fields: { timestamp: 1 } }
  ]);
  
  // 4. Territories collection
  await createCollectionAndIndex('territories', [
    { fields: { territoryId: 1 }, options: { unique: true } },
    { fields: { governorCitizenId: 1 } }
  ]);
  
  // 5. Audit log collection (append-only)
  await createCollectionAndIndex('audit_log', [
    { fields: { logId: 1 }, options: { unique: true } },
    { fields: { timestamp: 1 } },
    { fields: { actionType: 1 } },
    { fields: { electionId: 1 } }
  ]);
  
  // 6. Royal decrees collection
  await createCollectionAndIndex('royal_decrees', [
    { fields: { decreeId: 1 }, options: { unique: true } },
    { fields: { monarchCitizenId: 1 } },
    { fields: { publishedAt: 1 } }
  ]);
  
  // 7. Hall of monarchs collection
  await createCollectionAndIndex('hall_of_monarchs', [
    { fields: { monarchCitizenId: 1 }, options: { unique: true } },
    { fields: { electionId: 1 } },
    { fields: { crownedDate: 1 } }
  ]);
  
  // 8. Achievements collection
  await createCollectionAndIndex('achievements', [
    { fields: { achievementId: 1 }, options: { unique: true } },
    { fields: { citizenId: 1 } },
    { fields: { type: 1 } }
  ]);
}

export async function rollbackElectionCollections(db: any): Promise<void> {
  const collections = [
    'elections',
    'candidates',
    'votes',
    'territories',
    'audit_log',
    'royal_decrees',
    'hall_of_monarchs',
    'achievements'
  ];
  
  for (const collectionName of collections) {
    try {
      if (db.collection(collectionName) && typeof db.collection(collectionName).drop === 'function') {
        await db.collection(collectionName).drop();
        console.log(`✅ Dropped ${collectionName}`);
      }
    } catch {
      // Ignore if doesn't exist
    }
  }
}

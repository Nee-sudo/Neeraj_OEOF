import { MongoClient, Db } from 'mongodb';
import dotenv from 'dotenv';

dotenv.config();

let mongoClient: MongoClient | null = null;
let mongoDb: Db | null = null;
let db: any;
let isMockDb = false;

// --- MOCK DATABASE FALLBACK (For offline sandbox ease and stability) ---
class MockDocRef {
  constructor(private collectionName: string, private docId: string, private mockDb: any) {}

  get path() {
    return `${this.collectionName}/${this.docId}`;
  }

  get ref() {
    return this;
  }

  async get() {
    const data = this.mockDb._data[this.collectionName]?.[this.docId];
    return {
      exists: data !== undefined,
      id: this.docId,
      ref: this,
      data: () => (data ? JSON.parse(JSON.stringify(data)) : undefined)
    };
  }

  async set(data: any) {
    if (!this.mockDb._data[this.collectionName]) {
      this.mockDb._data[this.collectionName] = {};
    }
    this.mockDb._data[this.collectionName][this.docId] = JSON.parse(JSON.stringify(data));
  }

  async update(data: any) {
    if (!this.mockDb._data[this.collectionName]) {
      this.mockDb._data[this.collectionName] = {};
    }
    const current = this.mockDb._data[this.collectionName][this.docId] || {};
    this.mockDb._data[this.collectionName][this.docId] = {
      ...current,
      ...JSON.parse(JSON.stringify(data))
    };
  }

  async delete() {
    if (this.mockDb._data[this.collectionName]) {
      delete this.mockDb._data[this.collectionName][this.docId];
    }
  }
}

class MockCollection {
  constructor(private collectionName: string, private mockDb: any, private limitVal: number = Infinity) {}

  doc(id: string) {
    return new MockDocRef(this.collectionName, id, this.mockDb);
  }

  limit(n: number) {
    return new MockCollection(this.collectionName, this.mockDb, n);
  }

  where(field: string, op: string, val: any) {
    const collections = this.mockDb._data[this.collectionName] || {};
    const filteredDocs: any[] = [];
    for (const [id, value] of Object.entries(collections)) {
      const valObj = value as any;
      if (op === '==' && valObj[field] === val) {
        filteredDocs.push({
          id,
          ref: new MockDocRef(this.collectionName, id, this.mockDb),
          data: () => JSON.parse(JSON.stringify(valObj))
        });
      }
    }

    return {
      get: async () => {
        const sliced = filteredDocs.slice(0, this.limitVal);
        return {
          empty: sliced.length === 0,
          docs: sliced
        };
      }
    };
  }

  async get() {
    const collections = this.mockDb._data[this.collectionName] || {};
    const docs = [];
    for (const [id, value] of Object.entries(collections)) {
      docs.push({
        id,
        ref: new MockDocRef(this.collectionName, id, this.mockDb),
        data: () => JSON.parse(JSON.stringify(value))
      });
    }
    const sliced = docs.slice(0, this.limitVal);
    return {
      empty: sliced.length === 0,
      docs: sliced
    };
  }
}

class MockFirestore {
  public _data: Record<string, Record<string, any>> = {};

  collection(name: string) {
    return new MockCollection(name, this);
  }

  async runTransaction(cb: (transaction: any) => Promise<any>) {
    const transaction = {
      get: async (docRef: MockDocRef) => {
        return docRef.get();
      },
      set: (docRef: MockDocRef, data: any) => {
        docRef.set(data);
        return transaction;
      },
      update: (docRef: MockDocRef, data: any) => {
        docRef.update(data);
        return transaction;
      },
      delete: (docRef: MockDocRef) => {
        docRef.delete();
        return transaction;
      }
    };
    return cb(transaction);
  }
}


// --- REAL MONGODB FIRESTORE ADAPTER IMPLEMENTATION ---

class MongoDocRef {
  constructor(private collectionName: string, private docId: string) {}

  get id() {
    return this.docId;
  }

  get path() {
    return `${this.collectionName}/${this.docId}`;
  }

  get ref() {
    return this;
  }

  async get() {
    if (!mongoDb) {
      throw new Error("Database not initialized");
    }
    const collection = mongoDb.collection(this.collectionName);
    const data = await collection.findOne({ _id: this.docId as any });
    return {
      exists: data !== null,
      id: this.docId,
      ref: this,
      data: () => {
        if (!data) return undefined;
        const { _id, ...rest } = data;
        return rest;
      }
    };
  }

  async set(data: any) {
    if (!mongoDb) {
      throw new Error("Database not initialized");
    }
    const collection = mongoDb.collection(this.collectionName);
    const documentToStore = { ...data, _id: this.docId };
    await collection.replaceOne({ _id: this.docId as any }, documentToStore, { upsert: true });
  }

  async update(data: any) {
    if (!mongoDb) {
      throw new Error("Database not initialized");
    }
    const collection = mongoDb.collection(this.collectionName);
    await collection.updateOne({ _id: this.docId as any }, { $set: data }, { upsert: true });
  }

  async delete() {
    if (!mongoDb) {
      throw new Error("Database not initialized");
    }
    const collection = mongoDb.collection(this.collectionName);
    await collection.deleteOne({ _id: this.docId as any });
  }
}

class MongoQuery {
  protected queryObj: Record<string, any> = {};
  protected limitVal: number = Infinity;

  constructor(protected collectionName: string) {}

  where(field: string, op: string, val: any) {
    if (op === '==') {
      this.queryObj[field] = val;
    }
    return this;
  }

  limit(n: number) {
    this.limitVal = n;
    return this;
  }

  async get() {
    if (!mongoDb) {
      throw new Error("Database not initialized");
    }
    const collection = mongoDb.collection(this.collectionName);
    let cursor = collection.find(this.queryObj);
    if (this.limitVal !== Infinity) {
      cursor = cursor.limit(this.limitVal);
    }
    const results = await cursor.toArray();

    const docs = results.map(doc => {
      const docId = String(doc._id || doc.id || '');
      const ref = new MongoDocRef(this.collectionName, docId);
      return {
        id: docId,
        ref,
        data: () => {
          const { _id, ...rest } = doc;
          return rest;
        }
      };
    });

    return {
      empty: docs.length === 0,
      docs
    };
  }
}

class MongoCollection extends MongoQuery {
  constructor(collName: string) {
    super(collName);
  }

  doc(id: string) {
    return new MongoDocRef(this.collectionName, id);
  }
}

class MongoTransaction {
  async get(docRef: MongoDocRef) {
    return docRef.get();
  }

  async set(docRef: MongoDocRef, data: any) {
    await docRef.set(data);
    return this;
  }

  async update(docRef: MongoDocRef, data: any) {
    await docRef.update(data);
    return this;
  }

  async delete(docRef: MongoDocRef) {
    await docRef.delete();
    return this;
  }
}

class MongoFirestore {
  collection(name: string) {
    return new MongoCollection(name);
  }

  async runTransaction(cb: (transaction: any) => Promise<any>) {
    const transaction = new MongoTransaction();
    return cb(transaction);
  }
}


// --- CONNECT / INITIALIZE EXPORTS ---

export const connectDatabase = async (): Promise<void> => {
  try {
    const mongoUri = process.env.MONGO_URI || 
                     process.env.MONGO_URL || 
                     process.env.MONGO_UR || 
                     process.env.Mongo_ur || 
                     process.env.DATABASE_URL || 
                     'mongodb://127.0.0.1:27017/oneearth';
                     
    console.log('⚡ MongoDB: Initializing connection...');
    // Mask credentials for clean logging output
    const maskedUri = mongoUri.replace(/:([^:@]{4,})@/, ':****@');
    console.log(`📡 Connection URI: ${maskedUri}`);

    mongoClient = new MongoClient(mongoUri, {
      connectTimeoutMS: 5000,
      socketTimeoutMS: 5000
    });
    
    await mongoClient.connect();
    mongoDb = mongoClient.db();
    console.log('✅ MongoDB: Connected successfully to the active cluster database.');
    isMockDb = false;
    db = new MongoFirestore();
  } catch (error: any) {
    console.error('❌ MongoDB Connection failed during server bootstrap:', error.message);
    console.warn('🔄 Falling back gracefully to offline, self-contained mock persistent sandbox.');
    isMockDb = true;
    db = new MockFirestore();
  }
};

export const getFirestoreDb = (): any => {
  if (!db) {
    if (isMockDb) {
      db = new MockFirestore();
    } else {
      db = new MongoFirestore();
    }
  }
  return db;
};

export const disconnectDatabase = async (): Promise<void> => {
  try {
    if (mongoClient) {
      await mongoClient.close();
      console.log('🔌 MongoDB: Connection closed safely.');
    }
  } catch (error: any) {
    console.error('❌ MongoDB: Error encountered during disconnect:', error.message);
  }
};

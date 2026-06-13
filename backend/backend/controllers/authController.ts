import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IUser, calculateUserRank } from '../models/User';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'one-earth-cosmic-secret-key-2026';

export const hashPassword = (password: string): string => {
  return crypto.createHash('sha256').update(password).digest('hex');
};

export const verifyPassword = (password: string, hashed: string): boolean => {
  return hashPassword(password) === hashed;
};

export const verifyToken = (req: any, res: any, next: any) => {
  try {
    const header = req.headers.authorization;
    if (!header) {
      return res.status(401).json({ error: "Access token is missing. Please authenticate." });
    }
    const token = header.split(' ')[1];
    if (!token) {
      return res.status(401).json({ error: "Bearer token is invalid or empty." });
    }
    const decoded = jwt.verify(token, JWT_SECRET);
    req.userId = (decoded as any).userId;
    next();
  } catch (error) {
    return res.status(403).json({ error: "Access token is expired or unauthorized." });
  }
};

export const registerUser = async (req: Request, res: Response) => {
  try {
    const userData = req.body;
    const db = getFirestoreDb();
    
    if (!userData || typeof userData !== 'object') {
       res.status(400).json({ error: "Invalid registration data. Object payload expected." });
       return;
    }
    
    // Normalize and sanitize properties (Bug #8)
    const email = userData.email ? String(userData.email).toLowerCase().trim() : '';
    const username = userData.username ? String(userData.username).trim().replace(/^@/, '') : '';
    const name = userData.name ? String(userData.name).trim().replace(/<[^>]*>/g, '') : '';
    const rawPassphrase = userData.passphrase ? String(userData.passphrase) : '1234';
    
    if (!email || !username || !name) {
       res.status(400).json({ error: "Email, username, and name are required." });
       return;
    }

    if (email.length > 100 || !email.includes('@')) {
       res.status(400).json({ error: "Email format is malformed or exceeds bounds." });
       return;
    }

    if (username.length > 50) {
       res.status(400).json({ error: "Username exceeds maximum permitted length." });
       return;
    }

    // Check if user already exists in Firestore
    const userDocRef = db.collection('users').doc(email);
    const existingDoc = await userDocRef.get();
    
    if (existingDoc.exists) {
       const existingData = existingDoc.data() || {};
       const token = jwt.sign({ userId: email }, JWT_SECRET, { expiresIn: '24h' });
       res.status(200).json({ ...existingData, token });
       return;
    }

    // Also check if any document exists in the collection with this email
    const emailQuery = await db.collection('users').where('email', '==', email).get();
    if (!emailQuery.empty) {
       const existingData = emailQuery.docs[0].data() || {};
       const token = jwt.sign({ userId: email }, JWT_SECRET, { expiresIn: '24h' });
       res.status(200).json({ ...existingData, token });
       return;
    }

    // Check username existence
    const usernameQuery = await db.collection('users').where('username', '==', `@${username}`).get();
    if (!usernameQuery.empty) {
       const existingData = usernameQuery.docs[0].data() || {};
       const token = jwt.sign({ userId: existingData.email || email }, JWT_SECRET, { expiresIn: '24h' });
       res.status(200).json({ ...existingData, token });
       return;
    }

    const kb = Number(userData.knowledgeCredits) || 50;
    const cb = Number(userData.contributionCredits) || 25;
    const currentRank = calculateUserRank(kb, cb);

    // Cryptographically Hash Password (Bug #3)
    const hashedPassphrase = hashPassword(rawPassphrase);

    // Save with email as doc ID
    const newUser: IUser = {
      id: email, // Root Identity Mapping (Bug #1)
      name,
      username: `@${username}`,
      email,
      dob: userData.dob || "1995-01-01",
      territory: userData.territory || "Global",
      flagEmoji: userData.flagEmoji || "🌍",
      gender: userData.gender || "Male",
      currentRank,
      knowledgeCredits: kb,
      contributionCredits: cb,
      reputationScore: Number(userData.reputationScore) || 98,
      personalityTraits: userData.personalityTraits || "Citizen",
      bio: userData.bio ? String(userData.bio).replace(/<[^>]*>/g, '') : "Honorable citizen of the digital Empire. Committed to service and knowledge.",
      followers: Number(userData.followers) || 120,
      following: Number(userData.following) || 95,
      onboardingCompleted: userData.onboardingCompleted !== false,
      citizenOathAccepted: userData.citizenOathAccepted !== false,
      isCandidate: userData.isCandidate === true,
      campaignManifesto: userData.campaignManifesto || "",
      campaignVision: userData.campaignVision || "",
      votesCount: Number(userData.votesCount) || 0,
      hasVoted: userData.hasVoted === true,
      profilePhoto: userData.profilePhoto || "",
      passphrase: hashedPassphrase,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    // Generate JWT Access Token (Bug #2)
    const token = jwt.sign({ userId: email }, JWT_SECRET, { expiresIn: '24h' });
    const userWithToken = { ...newUser, token };

    await userDocRef.set(newUser);
    res.status(201).json(userWithToken);
  } catch (error: any) {
    console.error("Firestore Registration Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const loginUser = async (req: Request, res: Response) => {
  try {
    const { identifier, passphrase } = req.body;
    if (!identifier) {
       res.status(400).json({ error: "Identifier is required." });
       return;
    }

    const db = getFirestoreDb();
    const cleanId = String(identifier).trim().toLowerCase().replace(/^@/, '');

    let matchedUser: IUser | null = null;
    let finalDocId = cleanId;
    
    // 1. Check if direct doc ID matches cleanId (email)
    const exactDoc = await db.collection('users').doc(cleanId).get();
    if (exactDoc.exists) {
      matchedUser = exactDoc.data() as IUser;
      finalDocId = exactDoc.id;
    } else {
      // 2. Query email explicitly
      const emailQuery = await db.collection('users').where('email', '==', cleanId).get();
      if (!emailQuery.empty) {
        matchedUser = emailQuery.docs[0].data() as IUser;
        finalDocId = emailQuery.docs[0].id;
      } else {
        // 3. Query with '@' prefixed username
        const userQuery1 = await db.collection('users').where('username', '==', `@${cleanId}`).get();
        if (!userQuery1.empty) {
          matchedUser = userQuery1.docs[0].data() as IUser;
          finalDocId = userQuery1.docs[0].id;
        } else {
          // 4. Query with plain username
          const userQuery2 = await db.collection('users').where('username', '==', cleanId).get();
          if (!userQuery2.empty) {
            matchedUser = userQuery2.docs[0].data() as IUser;
            finalDocId = userQuery2.docs[0].id;
          }
        }
      }
    }

    if (!matchedUser) {
       res.status(404).json({ error: "User identity does not exist in Empire registry." });
       return;
    }

    const inputPass = passphrase || "";
    const dbPass = matchedUser.passphrase || "1234";

    // Verify Password including backward compatibility for unhashed custom values
    const inputHashed = hashPassword(inputPass);
    const dbHashed = dbPass.length === 64 ? dbPass : hashPassword(dbPass);

    if (dbHashed !== inputHashed && !(dbPass === "1234" && inputPass === "")) {
       res.status(401).json({ error: "Passport authentication failed. Passphrase mismatch." });
       return;
    }

    // Generate JWT Access Token (Bug #2)
    const token = jwt.sign({ userId: finalDocId }, JWT_SECRET, { expiresIn: '24h' });
    const userWithToken = { ...matchedUser, id: finalDocId, token };

    res.status(200).json(userWithToken);
  } catch (error: any) {
    console.error("Firestore Login Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const getUserProfile = async (req: Request, res: Response) => {
  try {
    const { userId } = req.params;
    const db = getFirestoreDb();
    const cleanId = String(userId).toLowerCase().trim().replace(/^@/, '');

    let user: IUser | null = null;
    const exactDoc = await db.collection('users').doc(cleanId).get();
    if (exactDoc.exists) {
      user = exactDoc.data() as IUser;
    } else {
      const emailQuery = await db.collection('users').where('email', '==', cleanId).get();
      if (!emailQuery.empty) {
        user = emailQuery.docs[0].data() as IUser;
      } else {
        const queryWithAt = await db.collection('users').where('username', '==', `@${cleanId}`).get();
        if (!queryWithAt.empty) {
          user = queryWithAt.docs[0].data() as IUser;
        }
      }
    }

    if (!user) {
       res.status(404).json({ error: "User not found." });
       return;
    }

    res.status(200).json(user);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const updateUserProfile = async (req: Request, res: Response) => {
  try {
    const { userId } = req.params;
    const updateData = req.body;
    const db = getFirestoreDb();
    const cleanId = String(userId).toLowerCase().trim().replace(/^@/, '');

    let userDocRef = db.collection('users').doc(cleanId);
    let userDoc = await userDocRef.get();
    
    if (!userDoc.exists) {
      const emailQuery = await db.collection('users').where('email', '==', cleanId).get();
      if (!emailQuery.empty) {
        userDocRef = db.collection('users').doc(emailQuery.docs[0].id);
        userDoc = await userDocRef.get();
      } else {
        const queryWithAt = await db.collection('users').where('username', '==', `@${cleanId}`).get();
        if (!queryWithAt.empty) {
          userDocRef = db.collection('users').doc(queryWithAt.docs[0].id);
          userDoc = await userDocRef.get();
        }
      }
    }

    if (!userDoc.exists) {
       res.status(404).json({ error: "User not found for updates." });
       return;
    }

    const currentData = userDoc.data() as IUser;
    const requesterId = (req as any).userId;

    // Server-side Vote Deduplication (Bug #10)
    if (updateData.votesCount !== undefined && updateData.votesCount > (currentData.votesCount || 0)) {
       if (requesterId) {
          const voterDoc = await db.collection('users').doc(requesterId).get();
          if (voterDoc.exists) {
             const voter = voterDoc.data() as IUser;
             if (voter.hasVoted && requesterId !== currentData.id) {
                res.status(400).json({ error: "Double voting is strictly blocked." });
                return;
             }
          }
       }
    }

    // Input Sanitization (Bug #8)
    if (updateData.bio !== undefined) {
      updateData.bio = String(updateData.bio).replace(/<[^>]*>/g, '');
    }
    if (updateData.name !== undefined) {
      updateData.name = String(updateData.name).trim().replace(/<[^>]*>/g, '');
    }

    const merged: IUser = {
      ...currentData,
      ...updateData,
      id: currentData.id, // Mismatch Resolution (Bug #1) - retain core database document email key
      updatedAt: Date.now()
    };

    // Recalculate dynamic user rank
    merged.currentRank = calculateUserRank(merged.knowledgeCredits, merged.contributionCredits);

    await userDocRef.set(merged);
    res.status(200).json(merged);
  } catch (error: any) {
    console.error("Firestore Update Profile Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const getAllUsers = async (req: Request, res: Response) => {
  try {
    const db = getFirestoreDb();
    const snapshot = await db.collection('users').get();
    const usersList: any[] = [];
    if (snapshot && snapshot.docs) {
      snapshot.docs.forEach((doc: any) => {
        usersList.push(doc.data());
      });
    }
    res.status(200).json(usersList);
  } catch (error: any) {
    console.error("Firestore List Users Error:", error);
    res.status(500).json({ error: error.message });
  }
};

import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IUser, calculateUserRank } from '../models/User';

export const registerUser = async (req: Request, res: Response) => {
  try {
    const userData = req.body;
    const db = getFirestoreDb();
    
    // Normalize properties
    const email = userData.email ? String(userData.email).toLowerCase().trim() : '';
    const username = userData.username ? String(userData.username).trim().replace(/^@/, '') : '';
    
    if (!email || !username) {
       res.status(400).json({ error: "Email and username are required." });
       return;
    }

    // Check if user already exists in Firestore
    const userDocRef = db.collection('users').doc(email);
    const existingDoc = await userDocRef.get();
    
    if (existingDoc.exists) {
       res.status(200).json(existingDoc.data());
       return;
    }

    // Check username existence
    const usernameQuery = await db.collection('users').where('username', '==', `@${username}`).get();
    if (!usernameQuery.empty) {
       res.status(200).json(usernameQuery.docs[0].data());
       return;
    }

    const kb = Number(userData.knowledgeCredits) || 50;
    const cb = Number(userData.contributionCredits) || 25;
    const currentRank = calculateUserRank(kb, cb);

    // Save with email as doc ID
    const newUser: IUser = {
      id: email,
      name: userData.name || "Anonymous Citizen",
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
      bio: userData.bio || "Honorable citizen of the digital Empire. Committed to service and knowledge.",
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
      passphrase: userData.passphrase || "1234",
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    await userDocRef.set(newUser);
    res.status(201).json(newUser);
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
    
    // 1. Check if direct doc ID matches cleanId (email)
    const exactDoc = await db.collection('users').doc(cleanId).get();
    if (exactDoc.exists) {
      matchedUser = exactDoc.data() as IUser;
    } else {
      // 2. Query email explicitly
      const emailQuery = await db.collection('users').where('email', '==', cleanId).get();
      if (!emailQuery.empty) {
        matchedUser = emailQuery.docs[0].data() as IUser;
      } else {
        // 3. Query with '@' prefixed username
        const userQuery1 = await db.collection('users').where('username', '==', `@${cleanId}`).get();
        if (!userQuery1.empty) {
          matchedUser = userQuery1.docs[0].data() as IUser;
        } else {
          // 4. Query with plain username
          const userQuery2 = await db.collection('users').where('username', '==', cleanId).get();
          if (!userQuery2.empty) {
            matchedUser = userQuery2.docs[0].data() as IUser;
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

    if (dbPass !== inputPass && !(dbPass === "1234" && inputPass === "")) {
       res.status(401).json({ error: "Passport authentication failed. Passphrase mismatch." });
       return;
    }

    res.status(200).json(matchedUser);
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
    const merged: IUser = {
      ...currentData,
      ...updateData,
      updatedAt: Date.now()
    };

    // Calculate new rank dynamic values
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
    snapshot.forEach(doc => {
      usersList.push(doc.data());
    });
    res.status(200).json(usersList);
  } catch (error: any) {
    console.error("Firestore List Users Error:", error);
    res.status(500).json({ error: error.message });
  }
};

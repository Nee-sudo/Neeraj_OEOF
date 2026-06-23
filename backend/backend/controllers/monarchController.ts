import { Request, Response } from 'express';
import { MonarchService } from '../services/MonarchService';
import { getFirestoreDb } from '../config/database';
import { calculateUserRank } from '../models/User';
import crypto from 'crypto';

export const reconcileRanksInDatabase = async (db: any) => {
  try {
    const snapshot = await db.collection('users').get();
    if (!snapshot || snapshot.empty) return { king: null, queen: null };

    const users = snapshot.docs.map((doc: any) => ({
      _id: doc.id,
      ...doc.data()
    }));

    // Find candidate Kings (males)
    const males = users.filter((u: any) => u.gender && u.gender.toLowerCase() === 'male');
    males.sort((a: any, b: any) => {
      const creditsA = (a.knowledgeCredits || 0) + (a.contributionCredits || 0);
      const creditsB = (b.knowledgeCredits || 0) + (b.contributionCredits || 0);
      return creditsB - creditsA;
    });

    // Find candidate Queens (females)
    const females = users.filter((u: any) => u.gender && u.gender.toLowerCase() === 'female');
    females.sort((a: any, b: any) => {
      const creditsA = (a.knowledgeCredits || 0) + (a.contributionCredits || 0);
      const creditsB = (b.knowledgeCredits || 0) + (b.contributionCredits || 0);
      return creditsB - creditsA;
    });

    const king = males[0] || null;
    const queen = females[0] || null;

    // Write updates to database to ensure only one King and one Queen at a time.
    for (const u of users) {
      let changed = false;
      let newRank = u.currentRank;

      if (king && u._id === king._id) {
        if (u.currentRank !== 'King') {
          newRank = 'King';
          changed = true;
        }
      } else if (queen && u._id === queen._id) {
        if (u.currentRank !== 'Queen') {
          newRank = 'Queen';
          changed = true;
        }
      } else {
        if (u.currentRank === 'King' || u.currentRank === 'Queen') {
          newRank = calculateUserRank(
            u.knowledgeCredits || 0,
            u.contributionCredits || 0,
            u.reputationScore || 25,
            u.civicParticipationScore || 0,
            u.legacyPoints || 0
          );
          changed = true;
        }
      }

      if (changed) {
        await db.collection('users').doc(u._id).update({
          currentRank: newRank
        });
        u.currentRank = newRank;
      }
    }

    return { king, queen };
  } catch (error) {
    console.error("Error reconciling ranks in database:", error);
    return { king: null, queen: null };
  }
};

export const postRoyalDecree = async (req: Request, res: Response) => {
  try {
    const monarchId = (req as any).userId;
    const { type, title, content, rewardDescription, targetParticipants, visibility, targetTerritoryId } = req.body;
    
    if (!monarchId) {
      return res.status(401).json({ error: 'Authentication is required.' });
    }
    
    const result = await MonarchService.postRoyalDecree(monarchId, {
      type,
      title,
      content,
      rewardDescription,
      targetParticipants,
      visibility,
      targetTerritoryId
    });
    
    if (!result.success) {
      return res.status(403).json({ success: false, error: result.error });
    }
    
    return res.status(201).json({
      success: true,
      decreeId: result.decreeId,
      message: 'Royal Imperial Decree has been successfully recorded and broadcast.'
    });
  } catch (error: any) {
    console.error('❌ MonarchController postRoyalDecree error:', error.message);
    return res.status(500).json({ error: 'System fault publishing decree.' });
  }
};

export const getCurrentMonarch = async (req: Request, res: Response) => {
  try {
    const db = getFirestoreDb();
    const { king, queen } = await reconcileRanksInDatabase(db);
    
    const monarch = king || queen || null;
    
    return res.json({
      success: true,
      monarch: monarch ? {
        id: monarch._id,
        name: monarch.name,
        username: monarch.username,
        currentRank: monarch.currentRank,
        auraLevel: monarch.auraLevel || 'None',
        bio: monarch.bio || '',
        profilePhoto: monarch.profilePhoto || ''
      } : null,
      king: king ? {
        id: king._id,
        name: king.name,
        username: king.username,
        currentRank: king.currentRank,
        auraLevel: king.auraLevel || 'None',
        bio: king.bio || '',
        profilePhoto: king.profilePhoto || ''
      } : null,
      queen: queen ? {
        id: queen._id,
        name: queen.name,
        username: queen.username,
        currentRank: queen.currentRank,
        auraLevel: queen.auraLevel || 'None',
        bio: queen.bio || '',
        profilePhoto: queen.profilePhoto || ''
      } : null
    });
  } catch (error: any) {
    console.error('❌ MonarchController getCurrentMonarch error:', error.message);
    return res.status(500).json({ error: 'Failed to retrieve current throne holder.' });
  }
};

export const appointCouncilMember = async (req: Request, res: Response) => {
  try {
    const monarchId = (req as any).userId;
    const { citizenId, councilRole } = req.body;
    
    if (!monarchId) {
      return res.status(401).json({ error: 'Authentication is required.' });
    }
    
    // Formally appoint council member using the MonarchService
    const serviceResult = await MonarchService.appointCouncilMember(monarchId, citizenId, councilRole || 'Elder');
    if (!serviceResult.success) {
      return res.status(403).json({ error: serviceResult.error });
    }
    
    // Bestow 'ROYAL_MENTOR' achievement onto the appointee as their council badge
    const db = getFirestoreDb();
    const achId = `badge_${crypto.randomUUID()}`;
    await db.collection('achievements').doc(achId).set({
      achievementId: achId,
      citizenId,
      type: 'ROYAL_MENTOR',
      title: `Imperial Council Advisor (${councilRole || 'Elder'})`,
      description: `Formally appointed to the Sovereign Advisory Council of One Earth by direct Monarchy selection.`,
      iconUrl: "council_emblem",
      isRoyal: true,
      isMandatory: true,
      unlockedAt: Date.now(),
      contributionValue: 150,
      earningReason: 'Imperial Council appointment',
      createdAt: Date.now()
    });
    
    return res.json({
      success: true,
      message: 'Council Advisor successfully appointed and royal badge bestowed.'
    });
  } catch (error: any) {
    console.error('❌ MonarchController appointCouncilMember error:', error.message);
    return res.status(500).json({ error: 'Failed to complete council appointment.' });
  }
};

export const getHallOfMonarchs = async (req: Request, res: Response) => {
  try {
    const db = getFirestoreDb();
    const snap = await db.collection('hall_of_monarchs').get();
    const records = snap.docs.map((doc: any) => doc.data());
    
    // Symmetrical sort descending by crown date
    records.sort((a: any, b: any) => b.crownedDate - a.crownedDate);
    
    return res.json({ success: true, hall: records });
  } catch (error: any) {
    console.error('❌ MonarchController getHallOfMonarchs error:', error.message);
    return res.status(500).json({ error: 'Failed to fetch hall of monarchs historical records.' });
  }
};

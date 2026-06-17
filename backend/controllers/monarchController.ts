import { Request, Response } from 'express';
import { MonarchService } from '../services/MonarchService';
import { getFirestoreDb } from '../config/database';
import crypto from 'crypto';

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
    
    // Find users with Rank King or Queen
    const kingSnapshot = await db.collection('users').where('currentRank', '==', 'King').get();
    let monarchDoc = kingSnapshot.empty ? null : kingSnapshot.docs[0];
    
    if (!monarchDoc) {
      const queenSnapshot = await db.collection('users').where('currentRank', '==', 'Queen').get();
      monarchDoc = queenSnapshot.empty ? null : queenSnapshot.docs[0];
    }
    
    if (!monarchDoc) {
      return res.json({ success: true, monarch: null, message: "Throne currently vacant. Next election pending." });
    }
    
    const data = monarchDoc.data();
    // Return sanitized monarch profile
    return res.json({
      success: true,
      monarch: {
        id: data.id,
        name: data.name,
        username: data.username,
        currentRank: data.currentRank,
        auraLevel: data.auraLevel || 'None',
        bio: data.bio || '',
        profilePhoto: data.profilePhoto || ''
      }
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

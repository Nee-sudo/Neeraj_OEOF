import { Request, Response } from 'express';
import { MonarchProfileService, RoyalMemberService } from '../services/MonarchProfileService';
import { getFirestoreDb } from '../config/database';

export const getMonarchProfile = async (req: Request, res: Response) => {
  try {
    const { monarchId } = req.params;
    
    const profile = await MonarchProfileService.getMonarchProfile(monarchId);
    if (!profile) {
      return res.status(404).json({ error: 'Monarch not found or invalid ID' });
    }
    
    return res.json({
      success: true,
      profile
    });
  } catch (error: any) {
    console.error('Error fetching monarch profile:', error);
    return res.status(500).json({ error: 'Failed to fetch monarch profile' });
  }
};

export const getThroneWorthiness = async (req: Request, res: Response) => {
  try {
    const { monarchId } = req.params;
    const db = getFirestoreDb();
    
    const userDoc = await db.collection('users').doc(monarchId).get();
    if (!userDoc.exists) {
      return res.status(404).json({ error: 'User not found' });
    }
    
    const worthiness = MonarchProfileService.calculateThroneWorthiness(userDoc.data() as any);
    return res.json({
      success: true,
      worthiness
    });
  } catch (error: any) {
    console.error('Error calculating throne worthiness:', error);
    return res.status(500).json({ error: 'Failed to calculate throne worthiness' });
  }
};

export const getRoyalMemberProfile = async (req: Request, res: Response) => {
  try {
    const { memberId } = req.params;
    
    const profile = await RoyalMemberService.getRoyalMemberProfile(memberId);
    if (!profile) {
      return res.status(404).json({ error: 'Member not found' });
    }
    
    return res.json({
      success: true,
      profile
    });
  } catch (error: any) {
    console.error('Error fetching royal member profile:', error);
    return res.status(500).json({ error: 'Failed to fetch royal member profile' });
  }
};

export const getImperialHierarchy = async (req: Request, res: Response) => {
  try {
    const db = getFirestoreDb();
    
    // Get all users with royal/senior ranks
    const snapshot = await db.collection('users')
      .where('currentRank', 'in', ['King', 'Queen', 'Prince', 'Princess', 'Duke', 'Duchess', 'Noble', 'Sage', 'Scholar'])
      .get();
    
    const members = snapshot.docs.map(doc => {
      const user = doc.data() as any;
      return {
        id: user.id || user.email,
        name: user.name,
        rank: user.currentRank,
        aura: user.auraLevel || 'None',
        kc: user.knowledgeCredits || 0,
        cc: user.contributionCredits || 0
      };
    });
    
    // Sort logic (King/Queen first, then rank order, then merit)
    const rankPriority: Record<string, number> = {
      'King': 1, 'Queen': 1,
      'Prince': 2, 'Princess': 2,
      'Duke': 3, 'Duchess': 3,
      'Noble': 4,
      'Sage': 5,
      'Scholar': 6
    };
    
    members.sort((a, b) => {
      const priorityA = rankPriority[a.rank] || 10;
      const priorityB = rankPriority[b.rank] || 10;
      if (priorityA !== priorityB) {
        return priorityA - priorityB;
      }
      return (b.kc + b.cc) - (a.kc + a.cc); // higher merit score breaks tie
    });
    
    return res.json({
      success: true,
      hierarchy: members
    });
  } catch (error: any) {
    console.error('Error fetching imperial hierarchy:', error);
    return res.status(500).json({ error: 'Failed to fetch imperial hierarchy' });
  }
};

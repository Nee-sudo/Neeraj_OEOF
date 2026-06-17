import { Request, Response } from 'express';
import { TerritoryService } from '../services/TerritoryService';
import { getFirestoreDb } from '../config/database';

export const getTerritoryLeaderboard = async (req: Request, res: Response) => {
  try {
    const leaderboard = await TerritoryService.calculateTerritoryLeaderboard();
    return res.json({
      success: true,
      leaderboard
    });
  } catch (error: any) {
    console.error('❌ TerritoryController getTerritoryLeaderboard error:', error.message);
    return res.status(500).json({ error: 'Failed to compile territory leaderboard data.' });
  }
};

export const getTerritoryMetrics = async (req: Request, res: Response) => {
  try {
    const { territoryId } = req.params;
    const db = getFirestoreDb();
    
    const territoryDoc = await db.collection('territories').doc(territoryId).get();
    
    if (!territoryDoc.exists) {
      return res.status(404).json({ error: 'Territory not found.' });
    }
    
    const territory = territoryDoc.data();
    return res.json({
      success: true,
      territory,
      metrics: {
        knowledgeGrowth: territory.currentMonth?.knowledgeGrowth || 0,
        contributionGrowth: territory.currentMonth?.contributionGrowth || 0,
        projectsCompleted: territory.currentMonth?.communityProjectsCompleted || 0,
        participationRate: territory.currentMonth?.learningParticipationRate || 0
      }
    });
  } catch (error: any) {
    console.error('❌ TerritoryController getTerritoryMetrics error:', error.message);
    return res.status(500).json({ error: 'Failed to fetch territory metrics.' });
  }
};

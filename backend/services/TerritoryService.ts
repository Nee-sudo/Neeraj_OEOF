import { getFirestoreDb } from '../config/database';
import { ITerritory, ITerritoryLeaderboardEntry } from '../models/Territory';
import { AuditActionType } from '../models/AuditLog';
import { logAuditEvent } from './AuditService';

export class TerritoryService {
  /**
   * Compiles monthly territory competitive leaderboard based on standard merit variables
   * Section 15 Architecture Metrics
   */
  static async calculateTerritoryLeaderboard(): Promise<ITerritoryLeaderboardEntry[]> {
    const db = getFirestoreDb();
    
    try {
      const snap = await db.collection('territories').get();
      const territories = snap.docs.map((d: any) => d.data() as ITerritory);
      
      const leaderboardEntries: ITerritoryLeaderboardEntry[] = [];
      
      for (const terr of territories) {
        const currentMonth = terr.currentMonth || {
          knowledgeGrowth: 0,
          contributionGrowth: 0,
          communityProjectsCompleted: 0,
          learningParticipationRate: 0
        };
        
        // Competition Score formula:
        // Score = 0.40 * KG + 0.30 * CG + 0.20 * PC + 0.10 * PR
        const score = 
          currentMonth.knowledgeGrowth * 0.40 +
          currentMonth.contributionGrowth * 0.30 +
          currentMonth.communityProjectsCompleted * 0.20 +
          currentMonth.learningParticipationRate * 0.10;
          
        leaderboardEntries.push({
          rank: 0, // Assigned below after sorting
          territoryId: terr.territoryId,
          name: terr.name,
          governorName: terr.governorCitizenId || 'Self-Governed',
          competitionScore: Math.round(score * 100) / 100,
          knowledgeGrowth: currentMonth.knowledgeGrowth,
          contributionGrowth: currentMonth.contributionGrowth,
          projectsCompleted: currentMonth.communityProjectsCompleted,
          participationRate: currentMonth.learningParticipationRate,
          rewards: terr.rewards || []
        });
      }
      
      // Sort descending by score
      leaderboardEntries.sort((a, b) => b.competitionScore - a.competitionScore);
      
      // Assign ranks & update records in database
      for (let i = 0; i < leaderboardEntries.length; i++) {
        const entry = leaderboardEntries[i];
        entry.rank = i + 1;
        
        await db.collection('territories').doc(entry.territoryId).update({
          currentCompetitionScore: entry.competitionScore,
          updatedAt: Date.now()
        });
      }
      
      return leaderboardEntries;
    } catch (err: any) {
      console.error(`❌ Errors compiling territory leaderboard:`, err.message);
      return [];
    }
  }

  /**
   * Distribute rewards (Banners, frames, monuments) to territory crown winners
   */
  static async distributeTerritoryRewards(territoryId: string, rewardType: 'RoyalBanner' | 'SpecialFrame' | 'TerritoryMonument', awardedFor: string): Promise<boolean> {
    const db = getFirestoreDb();
    try {
      const terrDoc = await db.collection('territories').doc(territoryId).get();
      if (!terrDoc.exists) return false;
      const terr = terrDoc.data() as ITerritory;
      
      const rewards = terr.rewards || [];
      rewards.push({
        rewardType,
        awardedAt: Date.now(),
        awardedFor
      });
      
      await db.collection('territories').doc(territoryId).update({
        rewards,
        updatedAt: Date.now()
      });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Distribute monthly rewards to winning top-3 territories
   */
  static async distributeMonthlyRewards(): Promise<{ success: boolean; error?: string }> {
    const db = getFirestoreDb();
    try {
      // 1. Get current leaderboard
      const leaderboard = await this.calculateTerritoryLeaderboard();
      
      if (leaderboard.length === 0) {
        return { success: false, error: 'No territories to award' };
      }
      
      // 2. Award top 3 territories
      const rewards = [
        { rank: 1, rewardType: 'RoyalBanner' as const, description: '1st place this month' },
        { rank: 2, rewardType: 'SpecialFrame' as const, description: '2nd place this month' },
        { rank: 3, rewardType: 'TerritoryMonument' as const, description: '3rd place this month' }
      ];
      
      for (const reward of rewards) {
        if (leaderboard[reward.rank - 1]) {
          const territory = leaderboard[reward.rank - 1];
          
          await this.distributeTerritoryRewards(territory.territoryId, reward.rewardType, reward.description);
          
          // Log audit event
          await logAuditEvent({
            actionType: AuditActionType.TERRITORY_REWARD_DISTRIBUTED,
            actionCategory: 'GOVERNANCE',
            details: {
              territoryId: territory.territoryId,
              rank: territory.rank,
              rewardType: reward.rewardType
            },
            status: 'SUCCESS'
          });
        }
      }
      
      return { success: true };
    } catch (error: any) {
      return { success: false, error: error.message };
    }
  }
}

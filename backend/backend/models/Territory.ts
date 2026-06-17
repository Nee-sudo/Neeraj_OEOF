export interface ITerritory {
  territoryId: string;
  name: string;
  
  // Governance
  governorCitizenId?: string;
  governorElectedAt?: number;
  
  // Competition metrics (monthly aggregates)
  currentMonth: {
    knowledgeGrowth: number;
    contributionGrowth: number;
    communityProjectsCompleted: number;
    learningParticipationRate: number; // 0-100%
    aggregatedAt: number;
  };
  
  previousMonth?: {
    knowledgeGrowth: number;
    contributionGrowth: number;
    communityProjectsCompleted: number;
    learningParticipationRate: number;
    leaderboardRank?: number;
    aggregatedAt: number;
  };
  
  // Territory competition score (calculated)
  currentCompetitionScore?: number;
  
  // Rewards held
  rewards: Array<{
    rewardType: 'RoyalBanner' | 'SpecialFrame' | 'TerritoryMonument';
    awardedAt: number;
    awardedFor: string; // e.g., "1st place March 2026"
  }>;
  
  // Metadata
  createdAt: number;
  updatedAt: number;
}

// Territory leaderboard entry
export interface ITerritoryLeaderboardEntry {
  rank: number;
  territoryId: string;
  name: string;
  governorName?: string;
  competitionScore: number;
  knowledgeGrowth: number;
  contributionGrowth: number;
  projectsCompleted: number;
  participationRate: number;
  rewards: ITerritory['rewards'];
}

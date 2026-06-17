export interface IMonarchProfile {
  monarchCitizenId: string;
  electionId: string;
  
  // Basic Info
  title: 'King' | 'Queen';
  reignStartDate: number;
  reignEndDate?: number;
  territoryId?: string;
  
  // Throne Worthiness (merit breakdown, 0-100)
  throneWorthiness: {
    percentage: number;
    knowledgeComponent: number;      // 50% weight
    contributionComponent: number;   // 25% weight
    reputationComponent: number;     // 15% weight
    publicSupportComponent: number;  // 10% weight
  };
  
  // Impact Stats (NOT based on followers)
  legacyPoints: number;
  citizensHelped: number;
  policiesInitiated: number;
  territoriesInfluenced: number;
  decreesPosted: number;
  
  // Visual Elements
  crownType: 'Imperial Gold' | 'Royal Diamond';
  auraType: 'Golden Sun' | 'Diamond Moon';
  profileFrame: 'Imperial Gold Frame' | 'Royal Diamond Frame';
  
  // Royal Timeline (journey from citizen to crown)
  timeline: Array<{
    milestone: string;
    rank: string;
    date: number;
  }>;
  
  // Approval Rating (NOT based on followers)
  approvalRating: number; // 0-100
  
  // Governance
  royalCouncil: string[]; // citizenId array
  royalSpeechIds: string[];
  royalInitiativeIds: string[];
  
  // Metadata
  createdAt: number;
  updatedAt: number;
}

export interface IRoyalMemberProfile {
  citizenId: string;
  rank: string; // Scholar, Sage, Noble, Duke, Duchess, Prince, Princess, etc.
  
  // Aura System (based on KC + CC only, followers ignored)
  auraLevel: 'None' | 'Bronze' | 'Silver' | 'Golden' | 'Imperial' | 'Legendary';
  
  // Merit Progression
  knowledgeCredits: number;
  contributionCredits: number;
  mergedCreditsTotal: number; // KC + CC for aura calculation
  
  // Royal Roles
  councilRole?: string; // "Advisor", "Scholar", "Elder", etc.
  achievements: string[]; // achievement IDs
  
  // Metadata
  profileUpdatedAt: number;
}

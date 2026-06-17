export interface IRoyalDecree {
  decreeId: string;
  monarchCitizenId: string;
  electionId?: string; // The election that made them monarch
  
  // Decree content
  type: 'DECREE' | 'INITIATIVE' | 'ANNOUNCEMENT' | 'MISSION';
  title: string;
  content: string;
  
  // Governance effect (for initiatives)
  rewardDescription?: string; // e.g., "+50 Knowledge Credits for participation"
  targetParticipants?: number; // Expected max participants
  
  // Visibility
  isPublished: boolean;
  visibility: 'GLOBAL' | 'TERRITORY';
  targetTerritoryId?: string;
  
  // Timestamps
  publishedAt: number;
  expiresAt?: number; // For missions/challenges
  
  // Engagement metrics
  viewCount: number;
  reactionsCount: {
    wise: number;
    helpful: number;
    inspiring: number;
  };
  
  // Metadata
  createdAt: number;
  updatedAt?: number; // Can update before publishing
  createdBy: string;
}

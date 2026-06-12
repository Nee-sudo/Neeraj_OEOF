export interface IPost {
  id: number; // Numerical sequential auto-increment ID
  authorId: string;
  authorName: string;
  authorUsername: string;
  authorRank: string;
  authorTerritory: string;
  authorFlag: string;
  content: string;
  category: string;
  timestamp: number;
  knowledgeValue: number;
  contributionProof: number;
  reputationImpact: number;
  reactedWiseUsers: string; // Comma separated user IDs
  reactedHelpfulUsers: string; // Comma separated user IDs
  reactedInspiringUsers: string; // Comma separated user IDs
}

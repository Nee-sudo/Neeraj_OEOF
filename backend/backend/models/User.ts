export interface IUser {
  id: string; // The user email serves as ID for synchronization
  name: string;
  username: string;
  email: string;
  dob: string;
  territory: string;
  flagEmoji: string;
  gender: string;
  currentRank: string;
  knowledgeCredits: number;
  contributionCredits: number;
  reputationScore: number;
  personalityTraits: string;
  bio: string;
  followers: number;
  following: number;
  onboardingCompleted: boolean;
  citizenOathAccepted: boolean;
  isCandidate: boolean;
  campaignManifesto: string;
  campaignVision: string;
  votesCount: number;
  hasVoted: boolean;
  profilePhoto: string;
  passphrase: string;
  createdAt?: number;
  updatedAt?: number;
  
  // Election-related extensions
  civicParticipationScore?: number;
  auraLevel?: 'None' | 'Bronze' | 'Silver' | 'Golden' | 'Imperial' | 'Legendary';
  banHistory?: Array<{ reason: string; bannedAt: number; bannedUntil?: number }>;
  legacyPoints?: number;
  lastActiveAt?: number;
  royalCouncilRole?: string;
  isCouncilMember?: boolean;
  royalSignatureEnabled?: boolean;
  royalTitle?: string;
  fcmToken?: string;
}

export const calculateUserRank = (
  knowledgeCredits: number,
  contributionCredits: number,
  reputationScore: number = 25,
  civicParticipationScore: number = 0,
  legacyPoints: number = 0
): string => {
  const thresholds = {
    'Citizen': { kc: 0, cc: 0, rep: 0 },
    'Scholar': { kc: 20, cc: 15, rep: 25 },
    'Sage': { kc: 50, cc: 40, rep: 50 },
    'Noble': { kc: 100, cc: 80, rep: 70 },
    'Duke': { kc: 150, cc: 120, rep: 80 },
    'Duchess': { kc: 150, cc: 120, rep: 80 },
    'Prince': { kc: 200, cc: 160, rep: 85 },
    'Princess': { kc: 200, cc: 160, rep: 85 },
    'KingCandidate': { kc: 250, cc: 200, rep: 90 },
    'QueenCandidate': { kc: 250, cc: 200, rep: 90 }
  };
  
  for (const [rank, reqs] of Object.entries(thresholds).reverse()) {
    if (
      (knowledgeCredits || 0) >= reqs.kc &&
      (contributionCredits || 0) >= reqs.cc &&
      (reputationScore || 0) >= reqs.rep
    ) {
      return rank;
    }
  }
  return 'Citizen';
};

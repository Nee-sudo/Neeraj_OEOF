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
}

export const calculateUserRank = (knowledge: number, contribution: number): string => {
  const total = (knowledge || 0) + (contribution || 0);
  if (total >= 300) {
    return 'Guardian';
  } else if (total >= 150) {
    return 'Contributor';
  } else {
    return 'Citizen';
  }
};

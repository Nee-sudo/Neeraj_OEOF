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

export interface RankConfig {
  name: string;
  minCredits: number;
  description: string;
}

export const RANK_PROGRESSION: RankConfig[] = [
  { name: 'Citizen', minCredits: 0, description: 'Entry rank' },
  { name: 'Contributor', minCredits: 150, description: 'Proven contributor' },
  { name: 'Guardian', minCredits: 300, description: 'Community guardian' },
  { name: 'Noble', minCredits: 550, description: 'Respected noble' },
  { name: 'Baron', minCredits: 800, description: 'Regional baron' },
  { name: 'Duke', minCredits: 1200, description: 'Powerful duke' },
  { name: 'Prince', minCredits: 1500, description: 'Royal prince/princess' },
  { name: 'Royal Candidate', minCredits: 2000, description: 'Eligible for throne' },
];

export const calculateUserRank = (knowledge: number, contribution: number, reputation: number = 98): string => {
  const total = (knowledge || 0) + (contribution || 0);
  
  if (total >= 2000 && (reputation || 0) >= 80) {
    return 'Royal Candidate';
  }

  for (let i = RANK_PROGRESSION.length - 1; i >= 0; i--) {
    if (total >= RANK_PROGRESSION[i].minCredits) {
      return RANK_PROGRESSION[i].name;
    }
  }
  return 'Citizen';
};

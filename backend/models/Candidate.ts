export interface ICandidate {
  candidateId: string;
  citizenId: string;
  electionId: string;
  
  // Campaign materials (required)
  visionStatement: string; // 100-500 chars (or 50-500 depending on check)
  manifesto: string; // 500-2000 chars (or 100-2000 depending on check)
  campaignSpeech: string; // 500-2000 chars (or 100-2000 depending on check)
  campaignVideoUrl?: string; // Optional for MVP
  
  // Eligibility snapshot (immutable at nomination time)
  eligibilityCheck: {
    knowledgeCredits: number;
    contributionCredits: number;
    reputationScore: number;
    civicParticipationScore: number;
    checkedAt: number;
    passed: boolean;
    failureReasons?: string[]; // If passed=false
  };
  
  // Status in nomination workflow
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'WITHDRAWN';
  
  // Results (populated after election closes)
  finalLeadershipScore?: number;
  finalVoteCount?: number;
  finalRank?: number;
  
  // Metadata
  nominatedAt: number;
  statusUpdatedAt: number;
  withdrawnAt?: number;
  rejectionReason?: string;
}

// Validation utility
export const validateCandidateNomination = (candidate: Partial<ICandidate>): {
  valid: boolean;
  errors: string[];
} => {
  const errors: string[] = [];
  
  if (!candidate.visionStatement || candidate.visionStatement.length < 50) {
    errors.push('Vision statement must be at least 50 characters');
  }
  if (candidate.visionStatement && candidate.visionStatement.length > 500) {
    errors.push('Vision statement must not exceed 500 characters');
  }
  
  if (!candidate.manifesto || candidate.manifesto.length < 100) {
    errors.push('Manifesto must be at least 100 characters');
  }
  if (candidate.manifesto && candidate.manifesto.length > 2000) {
    errors.push('Manifesto must not exceed 2000 characters');
  }
  
  if (!candidate.campaignSpeech || candidate.campaignSpeech.length < 100) {
    errors.push('Campaign speech must be at least 100 characters');
  }
  if (candidate.campaignSpeech && candidate.campaignSpeech.length > 2000) {
    errors.push('Campaign speech must not exceed 2000 characters');
  }
  
  return {
    valid: errors.length === 0,
    errors
  };
};

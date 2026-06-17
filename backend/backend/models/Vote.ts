export interface IVote {
  voteId: string;
  electionId: string;
  voterId: string; // Citizen casting the vote
  candidateId: string; // Candidate receiving the vote
  
  // Vote weight calculation (post-cap)
  weightApplied: number; // Usually 1.0, capped if saturation ceiling exceeded
  
  // Immutable audit trail
  timestamp: number; // Cannot be updated
  ipAddress?: string; // For additional fraud detection
  deviceId?: string; // For device tracking
  
  // Metadata
  createdAt: number;
}

// Database constraint (MUST be enforced at schema level)
export const VOTE_UNIQUE_CONSTRAINT = {
  name: 'vote_one_citizen_one_election',
  fields: ['electionId', 'voterId'],
  unique: true
};

// Vote validation
export const validateVote = (vote: Partial<IVote>): {
  valid: boolean;
  errors: string[];
} => {
  const errors: string[] = [];
  
  if (!vote.electionId) errors.push('electionId required');
  if (!vote.voterId) errors.push('voterId required');
  if (!vote.candidateId) errors.push('candidateId required');
  if (typeof vote.weightApplied !== 'number' || vote.weightApplied <= 0) {
    errors.push('weightApplied must be positive number');
  }
  if (vote.weightApplied > 1.0) {
    errors.push('weightApplied cannot exceed 1.0');
  }
  
  return {
    valid: errors.length === 0,
    errors
  };
};

// Vote saturation calculation
export const calculateVoteWeight = (
  totalVotesForCandidate: number,
  saturationCeiling: number
): number => {
  if (totalVotesForCandidate >= saturationCeiling) {
    return 0.5; // Half weight after ceiling reached
  }
  return 1.0; // Full weight until ceiling
};

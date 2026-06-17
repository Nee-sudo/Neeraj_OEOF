export interface IAuditLogEntry {
  logId: string;
  
  // Action classification
  actionType: AuditActionType;
  actionCategory: 'ELIGIBILITY' | 'VOTING' | 'SCORING' | 'GOVERNANCE' | 'NOMINATION' | 'RESULT';
  
  // Related entities
  electionId?: string;
  candidateId?: string;
  voterId?: string;
  citizenId?: string;
  monarchId?: string;
  
  // Action details
  details: {
    [key: string]: any;
  };
  
  // Immutable timestamp
  timestamp: number; // Cannot be updated
  
  // Operator info
  initiatedBy: string; // 'SYSTEM' or userId
  ipAddress?: string;
  
  // Outcome
  status: 'SUCCESS' | 'FAILURE';
  errorMessage?: string;
  
  // Metadata (immutable after creation)
  createdAt: number;
}

export enum AuditActionType {
  // Eligibility
  ELIGIBILITY_CHECK_STARTED = 'ELIGIBILITY_CHECK_STARTED',
  ELIGIBILITY_CHECK_COMPLETED = 'ELIGIBILITY_CHECK_COMPLETED',
  ELIGIBILITY_CHECK_FAILED = 'ELIGIBILITY_CHECK_FAILED',
  
  // Nomination
  NOMINATION_OPENED = 'NOMINATION_OPENED',
  CANDIDATE_NOMINATED = 'CANDIDATE_NOMINATED',
  CANDIDATE_APPROVED = 'CANDIDATE_APPROVED',
  CANDIDATE_REJECTED = 'CANDIDATE_REJECTED',
  CANDIDATE_WITHDRAWN = 'CANDIDATE_WITHDRAWN',
  
  // Voting
  VOTING_OPENED = 'VOTING_OPENED',
  VOTE_CAST = 'VOTE_CAST',
  VOTE_REJECTED_DUPLICATE = 'VOTE_REJECTED_DUPLICATE',
  VOTING_CLOSED = 'VOTING_CLOSED',
  
  // Scoring
  SCORING_STARTED = 'SCORING_STARTED',
  SCORE_CALCULATED = 'SCORE_CALCULATED',
  SCORING_COMPLETED = 'SCORING_COMPLETED',
  
  // Results
  RESULT_DECLARED = 'RESULT_DECLARED',
  WINNER_ANNOUNCED = 'WINNER_ANNOUNCED',
  REIGN_STARTED = 'REIGN_STARTED',
  REIGN_ENDED = 'REIGN_ENDED',
  RANK_UPDATED = 'RANK_UPDATED',
  
  // Governance
  ROYAL_DECREE_POSTED = 'ROYAL_DECREE_POSTED',
  COUNCIL_MEMBER_APPOINTED = 'COUNCIL_MEMBER_APPOINTED',
  COUNCIL_MEMBER_REMOVED = 'COUNCIL_MEMBER_REMOVED',
  TERRITORY_REWARD_DISTRIBUTED = 'TERRITORY_REWARD_DISTRIBUTED',
}

// Append-only storage contract
export const AUDIT_LOG_CONTRACT = {
  immutable: true,
  appendOnly: true,
  noDeletes: true,
  noUpdates: true,
  timestampImmutable: true
};

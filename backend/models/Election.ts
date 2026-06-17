import { ICandidate } from './Candidate';
import { IVote } from './Vote';

export interface IElection {
  electionId: string;
  type: 'King' | 'Queen' | 'TerritoryGovernor' | 'CrownOfWisdom' | 'CrownOfThePeople';
  territoryId?: string; // null for global King/Queen
  
  // Lifecycle state
  state: ElectionState;
  
  // Timeline
  nominationOpensAt: number;
  nominationClosesAt: number;
  votingOpensAt: number;
  votingClosesAt: number;
  
  // Configuration
  scoringFormulaVersion: string; // e.g., "v1", "v2"
  voteSaturationCeiling: number; // e.g., 100 max votes per candidate
  
  // Results
  winnerCandidateId?: string;
  winnerCitizenId?: string;
  finalLeadershipScore?: number;
  totalVotes?: number;
  
  // Metadata
  createdAt: number;
  createdBy: string; // Admin who created
  updatedAt: number;
}

export enum ElectionState {
  SCHEDULED = 'SCHEDULED',
  NOMINATION_OPEN = 'NOMINATION_OPEN',
  ELIGIBILITY_CHECK = 'ELIGIBILITY_CHECK',
  CANDIDATE_PENDING = 'CANDIDATE_PENDING',
  CANDIDATE_PUBLISHED = 'CANDIDATE_PUBLISHED',
  VOTING_OPEN = 'VOTING_OPEN',
  VOTING_CLOSED = 'VOTING_CLOSED',
  SCORE_CALCULATION = 'SCORE_CALCULATION',
  RESULT_DECLARED = 'RESULT_DECLARED',
  REIGN_ACTIVE = 'REIGN_ACTIVE',
  REIGN_ENDED = 'REIGN_ENDED'
}

export interface IElectionAggregate {
  election: IElection;
  candidates: ICandidate[];
  votes: IVote[];
  results: {
    leaderboard: Array<{
      candidateId: string;
      citizenId: string;
      name: string;
      leadershipScore: number;
      voteCount: number;
      rank: number;
    }>;
    winner?: {
      candidateId: string;
      citizenId: string;
      name: string;
      leadershipScore: number;
    };
  };
}

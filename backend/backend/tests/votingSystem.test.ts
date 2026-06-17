import { getFirestoreDb } from '../config/database';
import { VotingService } from '../services/VotingService';

describe('Voting System Enforcements', () => {
  beforeEach(() => {
    const db = getFirestoreDb();
    db._data = {
      elections: {
        'election_1': { electionId: 'election_1', state: 'VOTING_OPEN' },
        'election_closed': { electionId: 'election_closed', state: 'NOMINATION_OPEN' }
      },
      candidates: {
        'candidate_1': { candidateId: 'candidate_1', electionId: 'election_1' },
        'candidate_other': { candidateId: 'candidate_other', electionId: 'election_other' }
      },
      votes: {}
    };
  });

  test('Successfully cast a valid vote', async () => {
    const result = await VotingService.castVote('election_1', 'voter_abc', 'candidate_1');
    expect(result.success).toBe(true);
    expect(result.voteId).toBeDefined();

    // Verify it is saved in Mock DB
    const db = getFirestoreDb();
    const storedVotes = db._data['votes'] || {};
    const voteKey = Object.keys(storedVotes)[0];
    expect(storedVotes[voteKey]).toBeDefined();
    expect(storedVotes[voteKey].candidateId).toBe('candidate_1');
    expect(storedVotes[voteKey].voterId).toBe('voter_abc');
  });

  test('Reject double voting in same election (One-Citizen-One-Vote)', async () => {
    // Cast first vote
    const result1 = await VotingService.castVote('election_1', 'voter_xyz', 'candidate_1');
    expect(result1.success).toBe(true);

    // Cast second vote with same voter
    const result2 = await VotingService.castVote('election_1', 'voter_xyz', 'candidate_1');
    expect(result2.success).toBe(false);
    expect(result2.error).toContain('already voted');
  });

  test('Reject vote if election state is not VOTING_OPEN', async () => {
    const result = await VotingService.castVote('election_closed', 'voter_jkl', 'candidate_1');
    expect(result.success).toBe(false);
    expect(result.error).toContain('Voting is currently closed');
  });

  test('Reject vote if candidate is not in the election', async () => {
    const result = await VotingService.castVote('election_1', 'voter_jkl', 'candidate_other');
    expect(result.success).toBe(false);
    expect(result.error).toContain('candidate is not running');
  });
});

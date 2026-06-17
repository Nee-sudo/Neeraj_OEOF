import { getFirestoreDb } from '../config/database';
import { ElectionLifecycleService } from '../services/ElectionLifecycleService';
import { ElectionState } from '../models/Election';

describe('Election State Machine Lifecycle', () => {
  beforeEach(() => {
    const db = getFirestoreDb();
    db._data = {
      elections: {
        'election_active': {
          electionId: 'election_active',
          state: ElectionState.SCHEDULED,
          type: 'King',
          scoringFormulaVersion: 'v1',
          voteSaturationCeiling: 100
        }
      },
      candidates: {
        'candidate_active': {
          candidateId: 'candidate_active',
          citizenId: 'citizen_bob',
          electionId: 'election_active',
          nominatedAt: Date.now(),
          eligibilityCheck: {
            knowledgeCredits: 100,
            contributionCredits: 80,
            reputationScore: 90
          }
        }
      },
      votes: {
        'vote_1': {
          voteId: 'vote_1',
          electionId: 'election_active',
          voterId: 'voter_alice',
          candidateId: 'candidate_active'
        }
      },
      users: {
        'citizen_bob': { id: 'citizen_bob', currentRank: 'Sage', auraLevel: 'Golden' }
      },
      hall_of_monarchs: {},
      achievements: {}
    };
  });

  test('Valid transitions pass state validation checks', async () => {
    const result = await ElectionLifecycleService.transitionElectionState(
      'election_active',
      ElectionState.NOMINATION_OPEN
    );
    expect(result.success).toBe(true);

    const db = getFirestoreDb();
    expect(db._data['elections']['election_active'].state).toBe(ElectionState.NOMINATION_OPEN);
  });

  test('Reject invalid transitions', async () => {
    // Attempting to skip directly to VOTING_OPEN from SCHEDULED is illegal
    const result = await ElectionLifecycleService.transitionElectionState(
      'election_active',
      ElectionState.VOTING_OPEN
    );
    expect(result.success).toBe(false);
    expect(result.error).toContain('Invalid transition path');
  });

  test('SCORE_CALCULATION transition triggers vote tallying and scoring engine updates', async () => {
    // Progress state machine to target state first: SCHEDULED -> NOMINATION_OPEN -> ELIGIBILITY_CHECK -> CANDIDATE_PENDING -> CANDIDATE_PUBLISHED -> VOTING_OPEN -> VOTING_CLOSED -> SCORE_CALCULATION
    const db = getFirestoreDb();
    db._data['elections']['election_active'].state = ElectionState.VOTING_CLOSED;

    const result = await ElectionLifecycleService.transitionElectionState(
      'election_active',
      ElectionState.SCORE_CALCULATION
    );
    expect(result.success).toBe(true);

    // Verify candidate score calculations has been calculated and stored
    const candidate = db._data['candidates']['candidate_active'];
    expect(candidate.finalVoteCount).toBe(1);
    expect(candidate.finalLeadershipScore).toBeDefined();
    expect(candidate.finalLeadershipScore).toBeGreaterThan(0);
  });

  test('RESULT_DECLARED transition declares the winner, updates user rank, bestows achievements and historic Hall', async () => {
    const db = getFirestoreDb();
    db._data['elections']['election_active'].state = ElectionState.SCORE_CALCULATION;
    
    // Tally first
    db._data['candidates']['candidate_active'].finalLeadershipScore = 80;
    db._data['candidates']['candidate_active'].finalVoteCount = 1;

    const result = await ElectionLifecycleService.transitionElectionState(
      'election_active',
      ElectionState.RESULT_DECLARED
    );
    expect(result.success).toBe(true);

    // Verify User rank promoted to King
    const user = db._data['users']['citizen_bob'];
    expect(user.currentRank).toBe('King');
    expect(user.auraLevel).toBe('Imperial');

    // Verify Crown badge achievement awarded
    const achievements = db._data['achievements'];
    const badgeKey = Object.keys(achievements)[0];
    expect(achievements[badgeKey]).toBeDefined();
    expect(achievements[badgeKey].type).toBe('KING');
    expect(achievements[badgeKey].isRoyal).toBe(true);

    // Verify Hall of Monarchs logged
    const hallOfMonarchs = db._data['hall_of_monarchs'];
    expect(hallOfMonarchs['citizen_bob']).toBeDefined();
    expect(hallOfMonarchs['citizen_bob'].finalLeadershipScore).toBe(80);
  });
});

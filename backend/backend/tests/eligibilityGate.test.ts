import { getFirestoreDb } from '../config/database';
import { EligibilityService } from '../services/EligibilityService';

describe('Eligibility Gate Qualifications', () => {
  beforeEach(() => {
    const db = getFirestoreDb();
    db._data = {
      users: {
        'qualified_citizen': {
          id: 'qualified_citizen',
          name: 'Scholar Bob',
          citizenOathAccepted: true,
          knowledgeCredits: 30,
          contributionCredits: 20,
          reputationScore: 30,
          banHistory: []
        },
        'unaccepted_oath_citizen': {
          id: 'unaccepted_oath_citizen',
          name: 'Bob the Rebel',
          citizenOathAccepted: false,
          knowledgeCredits: 30,
          contributionCredits: 20,
          reputationScore: 30,
          banHistory: []
        },
        'underpower_celebrity': {
          id: 'underpower_celebrity',
          name: 'Celebrity Alice',
          citizenOathAccepted: true,
          knowledgeCredits: 5, // low
          contributionCredits: 2, // low
          reputationScore: 10, // low
          followers: 250000, // very high popularity!
          banHistory: []
        },
        'banned_citizen': {
          id: 'banned_citizen',
          name: 'Trouble Maker',
          citizenOathAccepted: true,
          knowledgeCredits: 40,
          contributionCredits: 30,
          reputationScore: 40,
          banHistory: [
            { reason: 'Trolling', bannedAt: Date.now() - 1000, bannedUntil: Date.now() + 100000 }
          ]
        }
      }
    };
  });

  test('Qualified citizen passes the eligibility check', async () => {
    const result = await EligibilityService.checkEligibility('qualified_citizen');
    expect(result.passed).toBe(true);
    expect(result.reasons.length).toBe(0);
  });

  test('Citizen who has not accepted Citizen Oath fails eligibility check', async () => {
    const result = await EligibilityService.checkEligibility('unaccepted_oath_citizen');
    expect(result.passed).toBe(false);
    expect(result.reasons).toContain('Citizen has not accepted the Citizen Oath');
  });

  test('High popularity (followers) does NOT bypass minimum qualification limits', async () => {
    const result = await EligibilityService.checkEligibility('underpower_celebrity');
    expect(result.passed).toBe(false);
    expect(result.reasons.some(r => r.includes('Knowledge Credits'))).toBe(true);
    expect(result.reasons.some(r => r.includes('Contribution Credits'))).toBe(true);
    expect(result.reasons.some(r => r.includes('Reputation Score'))).toBe(true);
  });

  test('Banned citizen fails eligibility check', async () => {
    const result = await EligibilityService.checkEligibility('banned_citizen');
    expect(result.passed).toBe(false);
    expect(result.reasons.some(r => r.includes('Citizen is banned'))).toBe(true);
  });
});

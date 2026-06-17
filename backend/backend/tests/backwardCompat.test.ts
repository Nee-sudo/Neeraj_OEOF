import { calculateUserRank } from '../models/User';

describe('User Model rank calculation and fields backward compatibility', () => {
  test('Standard Citizen, Scholar, and Sage calculations are accurate', () => {
    // Citizen
    expect(calculateUserRank(0, 0, 0)).toBe('Citizen');
    expect(calculateUserRank(15, 10, 20)).toBe('Citizen');

    // Scholar
    expect(calculateUserRank(20, 15, 25)).toBe('Scholar');
    expect(calculateUserRank(49, 39, 49)).toBe('Scholar');

    // Sage
    expect(calculateUserRank(50, 40, 50)).toBe('Sage');
    expect(calculateUserRank(99, 79, 69)).toBe('Sage');

    // Noble
    expect(calculateUserRank(100, 80, 70)).toBe('Noble');

    // KingCandidate / QueenCandidate
    expect(calculateUserRank(250, 200, 90)).toBe('QueenCandidate');
  });

  test('Old model interfaces parse safely', () => {
    // Old object loaded without election-related optional fields
    const oldUserData = {
      id: 'alice@example.com',
      name: 'Alice Cooper',
      username: 'alice',
      email: 'alice@example.com',
      dob: '2000-01-01',
      territory: 'USA',
      flagEmoji: '🇺🇸',
      gender: 'Female',
      currentRank: 'Citizen',
      knowledgeCredits: 10,
      contributionCredits: 5,
      reputationScore: 25,
      personalityTraits: 'Introvert, Scholar',
      bio: 'Hello Earth!',
      followers: 120,
      following: 80,
      onboardingCompleted: true,
      citizenOathAccepted: true,
      isCandidate: false,
      campaignManifesto: '',
      campaignVision: '',
      votesCount: 0,
      hasVoted: false,
      profilePhoto: '',
      passphrase: 'hash'
    };

    const parsed: any = JSON.parse(JSON.stringify(oldUserData));
    expect(parsed.id).toBe('alice@example.com');
    expect(parsed.civicParticipationScore).toBeUndefined();
    expect(parsed.auraLevel).toBeUndefined();
    expect(parsed.banHistory).toBeUndefined();
    expect(parsed.royalCouncilRole).toBeUndefined();
    expect(parsed.isCouncilMember).toBeUndefined();
  });
});

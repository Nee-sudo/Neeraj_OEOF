import { ScoringService } from '../services/ScoringService';

describe('Leadership Score Formula and Regression Verification', () => {
  test('Regression test: Merit-based Candidate A beats popularity-based Candidate B', () => {
    const result = ScoringService.regressionTest();
    expect(result.passed).toBe(true);
    expect(result.scoreA).toBeGreaterThan(result.scoreB);
    console.log(result.explanation);
  });

  test('Anti-popularity caps work properly when votes exceed saturation ceiling', () => {
    // 100 votes (at saturation ceiling limit)
    const resultAtCeiling = ScoringService.calculateLeadershipScore({
      knowledgeCredits: 100,
      contributionCredits: 100,
      reputationScore: 80,
      votesReceived: 100, // exact ceiling
      totalEligibleVoters: 1000,
      saturationCeiling: 100
    });

    // 200 votes (exceeds saturation ceiling)
    const resultOverCeiling = ScoringService.calculateLeadershipScore({
      knowledgeCredits: 100,
      contributionCredits: 100,
      reputationScore: 80,
      votesReceived: 200, // 100 over ceiling
      totalEligibleVoters: 1000,
      saturationCeiling: 100
    });

    // Effective votes calculation:
    // At ceiling: 100
    // Over ceiling: 100 + (200 - 100)*0.5 = 150
    const differenceOfNormalizedVotes = resultOverCeiling.components.votesNorm - resultAtCeiling.components.votesNorm;
    
    // Check if effective votes are used (e.g. 15% weight * difference in normalization)
    // 150 votes out of 1000 = 15%, 100 out of 1000 = 10%
    expect(resultAtCeiling.components.votesNorm).toBe(10); // 100 / 1000 * 100
    expect(resultOverCeiling.components.votesNorm).toBe(15); // 150 / 1000 * 100
    
    const expectedScoreA = 0.35 * 40 + 0.25 * 50 + 0.25 * 80 + 0.15 * 10; // (100/250)*100=40, (100/200)*100=50
    // expectedScoreA = 14 + 12.5 + 20 + 1.5 = 48.0
    expect(resultAtCeiling.leadershipScore).toBe(48);

    const expectedScoreB = 0.35 * 40 + 0.25 * 50 + 0.25 * 80 + 0.15 * 15;
    // expectedScoreB = 14 + 12.5 + 20 + 2.25 = 48.75 => rounded to 48.75
    expect(resultOverCeiling.leadershipScore).toBe(48.75);
  });
});

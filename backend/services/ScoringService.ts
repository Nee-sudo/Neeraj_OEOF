export interface ScoringInput {
  knowledgeCredits: number;
  contributionCredits: number;
  reputationScore: number;
  votesReceived: number;
  totalEligibleVoters?: number;
  saturationCeiling?: number;
  civicParticipationScore?: number;
}

export interface ScoringOutput {
  leadershipScore: number;
  components: {
    knowledgeNorm: number;
    contributionNorm: number;
    reputationNorm: number;
    votesNorm: number;
  };
  weights: {
    knowledge: number;
    contribution: number;
    reputation: number;
    votes: number;
  };
}

export class ScoringService {
  // v1 Formula weights (Section 5.1 of Audit Report / Architecture)
  private static readonly WEIGHTS_V1 = {
    knowledge: 0.35,
    contribution: 0.25,
    reputation: 0.25,
    votes: 0.15
  };
  
  // v2 Formula weights (with civic participations)
  private static readonly WEIGHTS_V2 = {
    knowledge: 0.40,
    contribution: 0.30,
    reputation: 0.20,
    votes: 0.10,
    civicParticipation: 0.10
  };
  
  /**
   * Calculate Leadership Score using v1 formula (MVP)
   * L = 0.35 * norm(KC) + 0.25 * norm(CC) + 0.25 * norm(Rep) + 0.15 * norm(Votes)
   */
  static calculateLeadershipScore(
    input: ScoringInput,
    formulaVersion: string = 'v1',
    saturationCeiling: number = 100
  ): any {
    if (formulaVersion === 'v2') {
      return this.calculateV2(input, saturationCeiling);
    }
    return this.calculateV1(input, saturationCeiling);
  }
  
  /**
   * v1 Formula Engine
   */
  private static calculateV1(input: ScoringInput, saturationCeiling: number): ScoringOutput {
    // Normalize credits to expected ranges
    const knowledgeNorm = this.normalize(input.knowledgeCredits, 0, 250);
    const contributionNorm = this.normalize(input.contributionCredits, 0, 200);
    const reputationNorm = Math.min(100, Math.max(0, input.reputationScore));
    
    // Normalize votes with anti-popularity saturation cap (Section 10 of Audit)
    const votesNorm = this.normalizeVotes(
      input.votesReceived,
      saturationCeiling,
      input.totalEligibleVoters || 1000
    );
    
    const leadershipScore =
      this.WEIGHTS_V1.knowledge * knowledgeNorm +
      this.WEIGHTS_V1.contribution * contributionNorm +
      this.WEIGHTS_V1.reputation * reputationNorm +
      this.WEIGHTS_V1.votes * votesNorm;
    
    return {
      leadershipScore: Math.round(leadershipScore * 100) / 100,
      components: {
        knowledgeNorm,
        contributionNorm,
        reputationNorm,
        votesNorm
      },
      weights: this.WEIGHTS_V1
    };
  }
  
  /**
   * v2 Formula Engine (Optioned)
   */
  private static calculateV2(input: ScoringInput, saturationCeiling: number): any {
    const knowledgeNorm = this.normalize(input.knowledgeCredits, 0, 250);
    const contributionNorm = this.normalize(input.contributionCredits, 0, 200);
    const reputationNorm = Math.min(100, Math.max(0, input.reputationScore));
    const civicNorm = Math.min(100, Math.max(0, input.civicParticipationScore || 0));
    
    const votesNorm = this.normalizeVotes(
      input.votesReceived,
      saturationCeiling,
      input.totalEligibleVoters || 1000
    );
    
    const leadershipScore =
      this.WEIGHTS_V2.knowledge * knowledgeNorm +
      this.WEIGHTS_V2.contribution * contributionNorm +
      this.WEIGHTS_V2.reputation * reputationNorm +
      this.WEIGHTS_V2.votes * votesNorm +
      this.WEIGHTS_V2.civicParticipation * civicNorm;
      
    return {
      leadershipScore: Math.round(leadershipScore * 100) / 100,
      components: {
        knowledgeNorm,
        contributionNorm,
        reputationNorm,
        votesNorm,
        civicNorm
      },
      weights: this.WEIGHTS_V2
    };
  }
  
  private static normalize(value: number, min: number, max: number): number {
    if (max <= min) return 0;
    const normalized = ((value - min) / (max - min)) * 100;
    return Math.min(100, Math.max(0, normalized));
  }
  
  /**
   * Anti-popularity Vote Saturation Ceiling Cap Logic (Section 10.2)
   */
  private static normalizeVotes(
    votesReceived: number,
    saturationCeiling: number,
    totalEligibleVoters: number
  ): number {
    let effectiveVotes = votesReceived;
    if (votesReceived > saturationCeiling) {
      // Votes exceeding the ceiling have 50% reduced weight
      const excess = votesReceived - saturationCeiling;
      effectiveVotes = saturationCeiling + excess * 0.5;
    }
    
    const maxPossibleWeight = totalEligibleVoters > 0 ? totalEligibleVoters : 1000;
    const normalized = (effectiveVotes / maxPossibleWeight) * 100;
    return Math.min(100, Math.max(0, normalized));
  }
  
  /**
   * Mandatory regression verification test that merit-based Candidate A beats Candidate B
   */
  static regressionTest(): {
    passed: boolean;
    scoreA: number;
    scoreB: number;
    explanation: string;
  } {
    const candidateA = {
      knowledgeCredits: 90,
      contributionCredits: 85,
      reputationScore: 88,
      votesReceived: 10,
      totalEligibleVoters: 1000,
      saturationCeiling: 100
    };
    
    const candidateB = {
      knowledgeCredits: 20,
      contributionCredits: 15,
      reputationScore: 30,
      votesReceived: 150, // Exceeds ceiling, popular
      totalEligibleVoters: 1000,
      saturationCeiling: 100
    };
    
    const resultA = this.calculateLeadershipScore(candidateA);
    const resultB = this.calculateLeadershipScore(candidateB);
    
    const scoreA = resultA.leadershipScore;
    const scoreB = resultB.leadershipScore;
    const passed = scoreA > scoreB;
    
    return {
      passed,
      scoreA,
      scoreB,
      explanation: passed
        ? `✅ PASS: Merit-driven Candidate A (${scoreA}) beats popularity-driven Candidate B (${scoreB}) under the anti-popularity weight caps.`
        : `❌ FAIL: Popularity-driven Candidate B (${scoreB}) bypasses merit checks and won against Candidate A (${scoreA}).`
    };
  }
}

// Log test outcome immediately on package import for verification
console.log('🧪 Scoring Engine Autonomic Sentry Activation...');
try {
  const check = ScoringService.regressionTest();
  console.log(check.explanation);
} catch (err: any) {
  console.error("❌ Scoring regression sentry failed to compile:", err.message);
}

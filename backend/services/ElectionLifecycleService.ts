import { getFirestoreDb } from '../config/database';
import { IElection, ElectionState } from '../models/Election';
import { ICandidate } from '../models/Candidate';
import { logAuditEvent } from './AuditService';
import { ScoringService } from './ScoringService';

const VALID_TRANSITIONS: Record<ElectionState, ElectionState[]> = {
  [ElectionState.SCHEDULED]: [ElectionState.NOMINATION_OPEN],
  [ElectionState.NOMINATION_OPEN]: [ElectionState.ELIGIBILITY_CHECK],
  [ElectionState.ELIGIBILITY_CHECK]: [ElectionState.CANDIDATE_PENDING],
  [ElectionState.CANDIDATE_PENDING]: [ElectionState.CANDIDATE_PUBLISHED],
  [ElectionState.CANDIDATE_PUBLISHED]: [ElectionState.VOTING_OPEN],
  [ElectionState.VOTING_OPEN]: [ElectionState.VOTING_CLOSED],
  [ElectionState.VOTING_CLOSED]: [ElectionState.SCORE_CALCULATION],
  [ElectionState.SCORE_CALCULATION]: [ElectionState.RESULT_DECLARED],
  [ElectionState.RESULT_DECLARED]: [ElectionState.REIGN_ACTIVE],
  [ElectionState.REIGN_ACTIVE]: [ElectionState.REIGN_ENDED],
  [ElectionState.REIGN_ENDED]: [] // Terminal
};

export class ElectionLifecycleService {
  /**
   * Transition state with rigorous validation and automatic state actions
   */
  static async transitionElectionState(
    electionId: string,
    newState: ElectionState
  ): Promise<{ success: boolean; error?: string }> {
    const db = getFirestoreDb();
    
    try {
      // 1. Fetch current election
      const electionDoc = await db.collection('elections').doc(electionId).get();
      if (!electionDoc.exists) {
        return { success: false, error: 'Election not found' };
      }
      
      const election = electionDoc.data() as IElection;
      const currentState = election.state;
      
      // 2. Validate transition path
      if (!VALID_TRANSITIONS[currentState]?.includes(newState)) {
        return {
          success: false,
          error: `Invalid transition path: Cannot move from ${currentState} to ${newState}`
        };
      }
      
      // 3. Perform automatic side-effects on state transitions
      if (newState === ElectionState.SCORE_CALCULATION) {
        // Tally votes and run ScoringEngine
        await this.tallyElectionsAndRunScoring(electionId);
      } else if (newState === ElectionState.RESULT_DECLARED) {
        // Formally finalize result
        await this.declareWinnerAndPromoteRank(electionId);
      } else if (newState === ElectionState.REIGN_ENDED) {
        // Retire current Monarch to Former
        await this.retireEmpiricalMonarch(electionId);
      }
      
      // 4. Persist updated state
      await db.collection('elections').doc(electionId).update({
        state: newState,
        updatedAt: Date.now()
      });
      
      // 5. System level Audit Trail log
      await logAuditEvent({
        actionType: 'RESULT_DECLARED' as any, // fallback representation
        actionCategory: 'RESULT',
        electionId,
        details: { fromState: currentState, toState: newState },
        status: 'SUCCESS'
      });
      
      return { success: true };
    } catch (err: any) {
      console.error(`❌ Election state transition failed for ${electionId} to ${newState}:`, err.message);
      return { success: false, error: `Transition error: ${err.message}` };
    }
  }
  
  /**
   * Section 5 & 10 Tallying Action
   */
  private static async tallyElectionsAndRunScoring(electionId: string): Promise<void> {
    const db = getFirestoreDb();
    
    // Fetch candidates running in this election
    const candDocs = await db.collection('candidates')
      .where('electionId', '==', electionId)
      .get();
      
    if (candDocs.empty) {
      console.warn("⚠️ No candidates ran in this election. Calculation aborted.");
      return;
    }
    
    // Fetch all votes cast in this election
    const voteDocs = await db.collection('votes')
      .where('electionId', '==', electionId)
      .get();
      
    const votesList = voteDocs.docs.map((d: any) => d.data());
    
    // Tally raw votes per candidate
    const voteTallies: Record<string, number> = {};
    for (const vote of votesList) {
      voteTallies[vote.candidateId] = (voteTallies[vote.candidateId] || 0) + 1;
    }
    
    const userDocs = await db.collection('users').get();
    const totalEligibleVoters = Math.max(10, userDocs.docs.length || 100);
    
    // Fetch the election parameters
    const electionDoc = await db.collection('elections').doc(electionId).get();
    const election = electionDoc.data();
    const saturationCeiling = election?.voteSaturationCeiling || 100;
    const formulaVer = election?.scoringFormulaVersion || 'v1';
    
    // Iterate and run Scoring Engine
    for (const candDoc of candDocs.docs) {
      const candidate = candDoc.data() as ICandidate;
      const votesReceived = voteTallies[candidate.candidateId] || 0;
      
      const scoringResult = ScoringService.calculateLeadershipScore({
        knowledgeCredits: candidate.eligibilityCheck.knowledgeCredits,
        contributionCredits: candidate.eligibilityCheck.contributionCredits,
        reputationScore: candidate.eligibilityCheck.reputationScore,
        votesReceived,
        totalEligibleVoters,
        saturationCeiling
      }, formulaVer);
      
      // Update Candidate record with calculations
      await db.collection('candidates').doc(candidate.candidateId).update({
        finalLeadershipScore: scoringResult.leadershipScore,
        finalVoteCount: votesReceived,
        statusUpdatedAt: Date.now()
      });
    }
    
    console.log(`📈 Scoring for election ${electionId} tallied and saved.`);
  }
  
  /**
   * Section 14.3 atomic advancement
   */
  private static async declareWinnerAndPromoteRank(electionId: string): Promise<void> {
    const db = getFirestoreDb();
    
    // Fetch updated candidates descending
    const candDocs = await db.collection('candidates')
      .where('electionId', '==', electionId)
      .get();
      
    const candidates = candDocs.docs.map((d: any) => d.data() as ICandidate);
    if (candidates.length === 0) return;
    
    // Sort descending by finalLeadershipScore, then nominatedAt
    candidates.sort((a, b) => {
      const diff = (b.finalLeadershipScore || 0) - (a.finalLeadershipScore || 0);
      if (diff !== 0) return diff;
      return a.nominatedAt - b.nominatedAt;
    });
    
    const winner = candidates[0];
    
    // Update candidates statuses & final Rank numbers
    for (let i = 0; i < candidates.length; i++) {
      const cand = candidates[i];
      await db.collection('candidates').doc(cand.candidateId).update({
        finalRank: i + 1,
        status: (i === 0) ? 'APPROVED' : 'PUBLISHED' // winner gets approved status
      });
    }
    
    // Formally update Election parameters and totals
    await db.collection('elections').doc(electionId).update({
      winnerCandidateId: winner.candidateId,
      winnerCitizenId: winner.citizenId,
      finalLeadershipScore: winner.finalLeadershipScore,
      totalVotes: candidates.reduce((sum, c) => sum + (c.finalVoteCount || 0), 0)
    });
    
    // Fetch election type to specify role
    const electionDoc = await db.collection('elections').doc(electionId).get();
    const type = electionDoc.data()?.type || 'King';
    const rankTitle = type === 'Queen' ? 'Queen' : (type === 'TerritoryGovernor' ? 'Governor' : 'King');
    
    // Update user rank and aura (Atomic promote)
    await db.collection('users').doc(winner.citizenId).update({
      currentRank: rankTitle,
      auraLevel: 'Imperial' // Imperial Aura bestowed upon Crown holder
    });
    
    // Add to historic hall of monarchs
    const hallId = `hall_${crypto.randomUUID()}`;
    await db.collection('hall_of_monarchs').doc(winner.citizenId).set({
      monarchCitizenId: winner.citizenId,
      electionId,
      crownedDate: Date.now(),
      finalLeadershipScore: winner.finalLeadershipScore,
      totalVotes: winner.finalVoteCount || 0,
      createdAt: Date.now()
    });
    
    // Unlock automatic system badge achievements (Section 14.3 / 8)
    const achId = `badge_${crypto.randomUUID()}`;
    await db.collection('achievements').doc(achId).set({
      achievementId: achId,
      citizenId: winner.citizenId,
      type: (type === 'Queen') ? 'QUEEN' : 'KING',
      title: `${rankTitle} of One Earth`,
      description: `Elected as Sovereign ${rankTitle} of One Earth in Election ${electionId} with score ${winner.finalLeadershipScore}`,
      iconUrl: "crown_emerald",
      isRoyal: true,
      isMandatory: true,
      unlockedAt: Date.now(),
      contributionValue: 500,
      earningReason: 'Elected empirical leader of the world',
      createdAt: Date.now()
    });
    
    console.log(`🏆 Winner declared: Citizen ${winner.citizenId} promoted to ${rankTitle}.`);
  }
  
  /**
   * Section 14.3 Strip / Former Badge retirement
   */
  private static async retireEmpiricalMonarch(electionId: string): Promise<void> {
    const db = getFirestoreDb();
    
    const electionDoc = await db.collection('elections').doc(electionId).get();
    if (!electionDoc.exists) return;
    const elect = electionDoc.data();
    const monarchId = elect.winnerCitizenId;
    if (!monarchId) return;
    
    // Strip royal overrides (Aura, permission back to regular scale)
    const userDoc = await db.collection('users').doc(monarchId).get();
    if (userDoc.exists) {
      const user = userDoc.data();
      await db.collection('users').doc(monarchId).update({
        currentRank: 'Scholar', // demotes safely to scholarly sage
        auraLevel: 'Golden' // falls back to highly-regarded golden status
      });
    }
    
    // Bestow Former Sovereign Badge
    const type = elect.type || 'King';
    const formerType = type === 'Queen' ? 'FORMER_QUEEN' : 'FORMER_KING';
    const achId = `badge_${crypto.randomUUID()}`;
    await db.collection('achievements').doc(achId).set({
      achievementId: achId,
      citizenId: monarchId,
      type: formerType,
      title: `Legacy Former ${type}`,
      description: `Held the sacred sovereignty crown for Reign Period of Election ${electionId}`,
      iconUrl: "silver_chalice",
      isRoyal: true,
      isMandatory: true,
      unlockedAt: Date.now(),
      contributionValue: 200,
      earningReason: 'Completed dedicated sovereignty cycle tenure.',
      createdAt: Date.now()
    });
    
    console.log(`💂 Monarch ${monarchId} retired safely to Scholar. Legacy badge awarded.`);
  }
}

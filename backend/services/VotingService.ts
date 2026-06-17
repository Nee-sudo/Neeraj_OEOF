import { getFirestoreDb } from '../config/database';
import { IVote } from '../models/Vote';
import { logAuditEvent } from './AuditService';
import crypto from 'crypto';

export interface VoteValidationResult {
  valid: boolean;
  errors: string[];
}

export class VotingService {
  /**
   * Cast a vote with full validation (one-citizen-one-vote)
   */
  static async castVote(
    electionId: string,
    voterId: string,
    candidateId: string
  ): Promise<{
    success: boolean;
    voteId?: string;
    error?: string;
  }> {
    const db = getFirestoreDb();
    
    try {
      // 1. Validate inputs
      const validation = this.validateVoteInput(electionId, voterId, candidateId);
      if (!validation.valid) {
        await logAuditEvent({
          actionType: 'VOTE_REJECTED_DUPLICATE' as any,
          actionCategory: 'VOTING',
          electionId,
          voterId,
          details: { errors: validation.errors },
          status: 'FAILURE'
        });
        return {
          success: false,
          error: validation.errors.join('; ')
        };
      }
      
      // 2. Clear checks if election actually exists and is VOTING_OPEN
      const electionDoc = await db.collection('elections').doc(electionId).get();
      if (!electionDoc.exists) {
        return { success: false, error: 'Election not found' };
      }
      const election = electionDoc.data();
      if (election.state !== 'VOTING_OPEN') {
        return { success: false, error: `Voting is currently closed (State: ${election.state})` };
      }
      
      // 3. Prevent duplicate vote (One-Citizen-One-Vote check)
      const existingVote = await db.collection('votes')
        .where('electionId', '==', electionId)
        .where('voterId', '==', voterId)
        .limit(1)
        .get();
      
      if (!existingVote.empty) {
        await logAuditEvent({
          actionType: 'VOTE_REJECTED_DUPLICATE' as any,
          actionCategory: 'VOTING',
          electionId,
          voterId,
          details: { reason: "Voter has already completed casting a ballot in this poll." },
          status: 'FAILURE'
        });
        return { success: false, error: 'You have already voted in this election' };
      }
      
      // 4. Validate that selected candidate is part of election nominations
      const candidateDoc = await db.collection('candidates').doc(candidateId).get();
      if (!candidateDoc.exists) {
        return { success: false, error: 'Candidate not found' };
      }
      const candidate = candidateDoc.data();
      if (candidate.electionId !== electionId) {
        return { success: false, error: 'Selected candidate is not running in this specified election' };
      }
      
      // 5. Construct secure unique Vote record
      const voteId = `vote_${crypto.randomUUID()}`;
      const vote: IVote = {
        voteId,
        electionId,
        voterId,
        candidateId,
        weightApplied: 1.0,
        timestamp: Date.now(),
        createdAt: Date.now()
      };
      
      // Save
      await db.collection('votes').doc(voteId).set(vote);
      
      // Log audit
      await logAuditEvent({
        actionType: 'VOTE_CAST' as any,
        actionCategory: 'VOTING',
        electionId,
        voterId,
        candidateId,
        details: { voteId },
        status: 'SUCCESS'
      });
      
      return {
        success: true,
        voteId
      };
    } catch (error: any) {
      const errorMsg = error.message || String(error);
      
      // Safeguard fallback constraint triggers
      if (errorMsg.includes('duplicate') || errorMsg.includes('E11000')) {
        await logAuditEvent({
          actionType: 'VOTE_REJECTED_DUPLICATE' as any,
          actionCategory: 'VOTING',
          electionId,
          voterId,
          details: { error: errorMsg },
          status: 'FAILURE'
        });
        return {
          success: false,
          error: 'You have already voted in this election (database constraint failsafe)'
        };
      }
      
      console.error('❌ Vote casting execution threw exception:', errorMsg);
      return {
        success: false,
        error: 'Vote submission failed due to database exception.'
      };
    }
  }
  
  private static validateVoteInput(
    electionId: string,
    voterId: string,
    candidateId: string
  ): VoteValidationResult {
    const errors: string[] = [];
    if (!electionId || typeof electionId !== 'string') errors.push('Invalid electionId parameter');
    if (!voterId || typeof voterId !== 'string') errors.push('Invalid voterId parameter');
    if (!candidateId || typeof candidateId !== 'string') errors.push('Invalid candidateId parameter');
    
    return {
      valid: errors.length === 0,
      errors
    };
  }
}

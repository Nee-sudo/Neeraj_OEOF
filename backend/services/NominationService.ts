import { ICandidate, validateCandidateNomination } from '../models/Candidate';
import { EligibilityService } from './EligibilityService';
import { getFirestoreDb } from '../config/database';
import { logAuditEvent } from './AuditService';
import { IUser } from '../models/User';
import crypto from 'crypto';

export class NominationService {
  static async nominateCandidate(
    citizenId: string,
    electionId: string,
    nominationData: {
      visionStatement: string;
      manifesto: string;
      campaignSpeech: string;
      campaignVideoUrl?: string;
    }
  ): Promise<{
    success: boolean;
    candidateId?: string;
    error?: string;
    eligibilityFailure?: string[];
  }> {
    const db = getFirestoreDb();
    
    try {
      // 1. Validate election exists and is in NOMINATION_OPEN state
      const electionDoc = await db.collection('elections').doc(electionId).get();
      if (!electionDoc.exists) {
        return { success: false, error: 'Election not found' };
      }
      const election = electionDoc.data();
      if (election.state !== 'NOMINATION_OPEN') {
        return { success: false, error: `Nominations are not currently open for this election (State: ${election.state})` };
      }
      
      // 2. Validate input strings lengths
      const partialCand: Partial<ICandidate> = {
        visionStatement: nominationData.visionStatement,
        manifesto: nominationData.manifesto,
        campaignSpeech: nominationData.campaignSpeech
      };
      
      const validationCheck = validateCandidateNomination(partialCand);
      if (!validationCheck.valid) {
        return { success: false, error: validationCheck.errors.join('; ') };
      }
      
      // 3. Check applicant’s eligibility under merit rule frameworks
      const eligibility = await EligibilityService.checkEligibility(citizenId);
      if (!eligibility.passed) {
        await logAuditEvent({
          actionType: 'CANDIDATE_REJECTED' as any,
          actionCategory: 'NOMINATION',
          electionId,
          citizenId,
          details: { reason: 'Failed eligibility validation checks', reasons: eligibility.reasons },
          status: 'FAILURE'
        });
        
        return {
          success: false,
          error: 'Citizen does not meet the specified minimum credit threshold requirements to stand as a candidate.',
          eligibilityFailure: eligibility.reasons
        };
      }
      
      // Get the complete citizen object to persist their static merit snapshot
      const userDoc = await db.collection('users').doc(citizenId).get();
      const citizen = userDoc.data() as IUser;
      
      // Check if user already nominated for this specific election
      const existingNomination = await db.collection('candidates')
        .where('electionId', '==', electionId)
        .where('citizenId', '==', citizenId)
        .limit(1)
        .get();
        
      if (!existingNomination.empty) {
        return { success: false, error: 'You are already registered/nominated in this election.' };
      }
      
      // 4. Create immutable historical snapshot structure of credits at submission
      const eligibilitySnapshot = EligibilityService.createEligibilitySnapshot(citizen);
      
      // 5. Construct candidate
      const candidateId = `cand_${crypto.randomUUID()}`;
      const candidate: ICandidate = {
        candidateId,
        citizenId,
        electionId,
        visionStatement: nominationData.visionStatement,
        manifesto: nominationData.manifesto,
        campaignSpeech: nominationData.campaignSpeech,
        campaignVideoUrl: nominationData.campaignVideoUrl,
        eligibilityCheck: eligibilitySnapshot,
        status: 'PENDING', // starts pending for approvals
        nominatedAt: Date.now(),
        statusUpdatedAt: Date.now()
      };
      
      await db.collection('candidates').doc(candidateId).set(candidate);
      
      // Log audit
      await logAuditEvent({
        actionType: 'CANDIDATE_NOMINATED' as any,
        actionCategory: 'NOMINATION',
        electionId,
        candidateId,
        citizenId,
        status: 'SUCCESS'
      });
      
      return {
        success: true,
        candidateId
      };
    } catch (err: any) {
      console.error('❌ Candidate nomination service execution failed:', err.message);
      return { success: false, error: 'Internal system fault encountered completing nomination submission.' };
    }
  }
}

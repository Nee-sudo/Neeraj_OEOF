import { IUser } from '../models/User';
import { getFirestoreDb } from '../config/database';
import { logAuditEvent } from './AuditService';

export interface EligibilityCheckResult {
  passed: boolean;
  reasons: string[];
  timestamp: number;
}

export class EligibilityService {
  private static MIN_KNOWLEDGE_CREDITS = 20;
  private static MIN_CONTRIBUTION_CREDITS = 15;
  private static MIN_REPUTATION_SCORE = 25;
  private static BAN_CHECK_ENABLED = true;
  
  /**
   * Check if a citizen is eligible to run for office
   * Architecture Reference Section 7
   */
  static async checkEligibility(citizenId: string): Promise<EligibilityCheckResult> {
    const db = getFirestoreDb();
    const reasons: string[] = [];
    
    try {
      // 1. Fetch citizen record
      const citizenDoc = await db.collection('users').doc(citizenId).get();
      if (!citizenDoc.exists) {
        reasons.push('Citizen record not found');
        return {
          passed: false,
          reasons,
          timestamp: Date.now()
        };
      }
      
      const citizen = citizenDoc.data() as IUser;
      
      // 2. Check verification status (Citizen Oath)
      if (!citizen.citizenOathAccepted) {
        reasons.push('Citizen has not accepted the Citizen Oath');
      }
      
      // 3. Check ban history
      if (this.BAN_CHECK_ENABLED && citizen.banHistory && citizen.banHistory.length > 0) {
        const activeBan = citizen.banHistory.find(ban => !ban.bannedUntil || ban.bannedUntil > Date.now());
        if (activeBan) {
          reasons.push(`Citizen is banned: ${activeBan.reason}`);
        }
      }
      
      // 4. Check knowledge credits
      if (!citizen.knowledgeCredits || citizen.knowledgeCredits < this.MIN_KNOWLEDGE_CREDITS) {
        reasons.push(
          `Minimum Knowledge Credits: ${this.MIN_KNOWLEDGE_CREDITS} required, ` +
          `${citizen.knowledgeCredits || 0} possessed`
        );
      }
      
      // 5. Check contribution credits
      if (!citizen.contributionCredits || citizen.contributionCredits < this.MIN_CONTRIBUTION_CREDITS) {
        reasons.push(
          `Minimum Contribution Credits: ${this.MIN_CONTRIBUTION_CREDITS} required, ` +
          `${citizen.contributionCredits || 0} possessed`
        );
      }
      
      // 6. Check reputation score
      if (!citizen.reputationScore || citizen.reputationScore < this.MIN_REPUTATION_SCORE) {
        reasons.push(
          `Minimum Reputation Score: ${this.MIN_REPUTATION_SCORE} required, ` +
          `${citizen.reputationScore || 0} possessed`
        );
      }
      
      // 7. CRITICAL: Verify popularity does NOT bypass eligibility (Section 7.2 Scenario 2)
      if (citizen.followers && citizen.followers > 100000) {
        console.warn(`⚠️ High-popularity citizen ${citizenId} (${citizen.followers} followers) ` +
          `is subject to the exact same eligibility criteria as other players.`);
      }
      
      // Log audit event
      const passed = reasons.length === 0;
      await logAuditEvent({
        actionType: passed ? 'ELIGIBILITY_CHECK_COMPLETED' as any : 'ELIGIBILITY_CHECK_FAILED' as any,
        actionCategory: 'ELIGIBILITY',
        citizenId,
        details: {
          knowledgeCredits: citizen.knowledgeCredits,
          contributionCredits: citizen.contributionCredits,
          reputationScore: citizen.reputationScore,
          failureReasons: reasons
        },
        status: passed ? 'SUCCESS' : 'FAILURE'
      });
      
      return {
        passed,
        reasons,
        timestamp: Date.now()
      };
    } catch (error: any) {
      const errorMsg = error instanceof Error ? error.message : String(error);
      console.error(`❌ Eligibility check failed for ${citizenId}:`, errorMsg);
      
      return {
        passed: false,
        reasons: ['System error during eligibility check'],
        timestamp: Date.now()
      };
    }
  }
  
  /**
   * Create immutable eligibility snapshot at nomination time
   */
  static createEligibilitySnapshot(citizen: IUser) {
    return {
      knowledgeCredits: citizen.knowledgeCredits || 0,
      contributionCredits: citizen.contributionCredits || 0,
      reputationScore: citizen.reputationScore || 0,
      civicParticipationScore: citizen.civicParticipationScore || 0,
      checkedAt: Date.now(),
      passed: true,
      failureReasons: undefined
    };
  }
}

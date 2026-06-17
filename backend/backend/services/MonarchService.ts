import { getFirestoreDb } from '../config/database';
import { IRoyalDecree } from '../models/RoyalDecree';
import { AuditActionType } from '../models/AuditLog';
import { logAuditEvent } from './AuditService';
import crypto from 'crypto';

export class MonarchService {
  /**
   * Promotes user state with crown and credentials
   */
  static async grantMonarchPowers(citizenId: string, electionId: string, type: string = 'King'): Promise<boolean> {
    const db = getFirestoreDb();
    try {
      const title = type === 'Queen' ? 'Queen' : 'King';
      await db.collection('users').doc(citizenId).update({
        currentRank: title,
        auraLevel: 'Imperial',
        updatedAt: Date.now()
      });
      
      await logAuditEvent({
        actionType: AuditActionType.REIGN_STARTED,
        actionCategory: 'GOVERNANCE',
        electionId,
        citizenId,
        details: { powerGranted: title },
        status: 'SUCCESS'
      });
      return true;
    } catch (err: any) {
      console.error(`❌ Error in grantMonarchPowers:`, err.message);
      return false;
    }
  }

  /**
   * Demotes user back to Sage scale
   */
  static async revokeMonarchPowers(citizenId: string, electionId?: string): Promise<boolean> {
    const db = getFirestoreDb();
    try {
      await db.collection('users').doc(citizenId).update({
        currentRank: 'Sage',
        auraLevel: 'Golden',
        updatedAt: Date.now()
      });
      
      await logAuditEvent({
        actionType: AuditActionType.REIGN_ENDED,
        actionCategory: 'GOVERNANCE',
        electionId,
        citizenId,
        details: { explanation: "Monarch powers safely revoked back to scholarly sage level." },
        status: 'SUCCESS'
      });
      return true;
    } catch (err: any) {
      console.error(`❌ Error in revokeMonarchPowers:`, err.message);
      return false;
    }
  }

  /**
   * Post imperial announcement decree safely locked to authentic monarchs
   */
  static async postRoyalDecree(
    monarchId: string,
    decreeData: {
      type: 'DECREE' | 'INITIATIVE' | 'ANNOUNCEMENT' | 'MISSION';
      title: string;
      content: string;
      rewardDescription?: string;
      targetParticipants?: number;
      visibility: 'GLOBAL' | 'TERRITORY';
      targetTerritoryId?: string;
    }
  ): Promise<{ success: boolean; decreeId?: string; error?: string }> {
    const db = getFirestoreDb();
    try {
      // Security Gate: Verify writer is actually a King or Queen
      const userDoc = await db.collection('users').doc(monarchId).get();
      if (!userDoc.exists) {
        return { success: false, error: 'User record non-existent' };
      }
      const user = userDoc.data();
      if (user.currentRank !== 'King' && user.currentRank !== 'Queen') {
        return { success: false, error: 'Sovereign permission denied. Only King/Queen is entitled to broadcast royal decrees.' };
      }
      
      const decreeId = `dec_${crypto.randomUUID()}`;
      const decree: IRoyalDecree = {
        decreeId,
        monarchCitizenId: monarchId,
        type: decreeData.type,
        title: decreeData.title,
        content: decreeData.content,
        rewardDescription: decreeData.rewardDescription,
        targetParticipants: decreeData.targetParticipants,
        isPublished: true,
        visibility: decreeData.visibility,
        targetTerritoryId: decreeData.targetTerritoryId,
        publishedAt: Date.now(),
        expiresAt: decreeData.type === 'MISSION' ? Date.now() + 7 * 24 * 60 * 60 * 1000 : undefined, // 7 days default
        viewCount: 0,
        reactionsCount: { wise: 0, helpful: 0, inspiring: 0 },
        createdAt: Date.now(),
        createdBy: monarchId
      };
      
      await db.collection('royal_decrees').doc(decreeId).set(decree);
      
      await logAuditEvent({
        actionType: AuditActionType.ROYAL_DECREE_POSTED,
        actionCategory: 'GOVERNANCE',
        citizenId: monarchId,
        details: { decreeId, type: decreeData.type, title: decreeData.title },
        status: 'SUCCESS'
      });
      
      return { success: true, decreeId };
    } catch (err: any) {
      console.error(`❌ Error posting decree:`, err.message);
      return { success: false, error: `System exception posting decree: ${err.message}` }; // Solved the minor bug in original template where it returned success: true on error catch
    }
  }

  /**
   * Appoint council member (monarch only)
   */
  static async appointCouncilMember(
    monarchId: string,
    memberCitizenId: string,
    role: string
  ): Promise<{ success: boolean; error?: string }> {
    const db = getFirestoreDb();
    try {
      // 1. Verify monarch
      const monarchDoc = await db.collection('users').doc(monarchId).get();
      if (!monarchDoc.exists) {
        return { success: false, error: 'User record non-existent' };
      }
      const monarch = monarchDoc.data();
      
      if (monarch.currentRank !== 'King' && monarch.currentRank !== 'Queen') {
        return { success: false, error: 'Only monarchs can appoint council members' };
      }
      
      // 2. Update member's status
      await db.collection('users').doc(memberCitizenId).update({
        royalCouncilRole: role,
        isCouncilMember: true,
        updatedAt: Date.now()
      });
      
      // 3. Log audit event
      await logAuditEvent({
        actionType: AuditActionType.COUNCIL_MEMBER_APPOINTED,
        actionCategory: 'GOVERNANCE',
        citizenId: monarchId,
        details: { appointedCitizenId: memberCitizenId, role },
        status: 'SUCCESS'
      });
      
      return { success: true };
    } catch (error: any) {
      return { success: false, error: error.message };
    }
  }
}

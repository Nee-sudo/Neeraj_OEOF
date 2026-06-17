import { getFirestoreDb } from '../config/database';
import { IMonarchProfile, IRoyalMemberProfile } from '../models/MonarchProfile';
import { IUser } from '../models/User';
import { logAuditEvent } from './AuditService';
import { AuditActionType } from '../models/AuditLog';

export class MonarchProfileService {
  /**
   * Get full royal profile for King/Queen
   * Includes throne worthiness, impact stats, timeline, governance hub
   */
  static async getMonarchProfile(monarchCitizenId: string): Promise<IMonarchProfile | null> {
    const db = getFirestoreDb();
    
    try {
      // 1. Get monarch user record
      const userDoc = await db.collection('users').doc(monarchCitizenId).get();
      if (!userDoc.exists) return null;
      
      const user = userDoc.data() as IUser;
      if (user.currentRank !== 'King' && user.currentRank !== 'Queen') {
        return null; // Not a monarch
      }
      
      // 2. Get or create monarch profile
      const profileDoc = await db.collection('monarch_profiles').doc(monarchCitizenId).get();
      if (!profileDoc.exists) {
        return await this.createMonarchProfile(monarchCitizenId, user);
      }
      
      const profile = profileDoc.data() as IMonarchProfile;
      
      // Automatically recalculate throne worthiness to keep up to date
      const worthiness = this.calculateThroneWorthiness(user);
      const updatedProfile: IMonarchProfile = {
        ...profile,
        throneWorthiness: {
          percentage: worthiness.percentage,
          knowledgeComponent: worthiness.components.knowledge,
          contributionComponent: worthiness.components.contribution,
          reputationComponent: worthiness.components.reputation,
          publicSupportComponent: worthiness.components.support
        },
        approvalRating: worthiness.percentage,
        updatedAt: Date.now()
      };
      
      await db.collection('monarch_profiles').doc(monarchCitizenId).set(updatedProfile);
      return updatedProfile;
    } catch (error) {
      console.error('Error fetching monarch profile:', error);
      return null;
    }
  }
  
  /**
   * Calculate throne worthiness (merit breakdown, 0-100)
   * NOT based on followers
   */
  static calculateThroneWorthiness(user: IUser): {
    percentage: number;
    components: { knowledge: number; contribution: number; reputation: number; support: number };
  } {
    // Normalize each component to 0-100
    const knowledgeNorm = Math.min(100, ((user.knowledgeCredits || 0) / 250) * 100); // 250 KC = 100%
    const contributionNorm = Math.min(100, ((user.contributionCredits || 0) / 200) * 100); // 200 CC = 100%
    const reputationNorm = user.reputationScore || 0; // Already 0-100
    
    // Support is based on post reactions or reputation, not followers!
    // For now, use reputation as proxy (would be calculated from community posts in production)
    const supportNorm = Math.max(0, (user.reputationScore || 0) - 10) * 1.111; // scale to 0-100 roughly
    const supportFinal = Math.min(100, supportNorm);
    
    // Apply weights: Knowledge 50%, Contribution 25%, Reputation 15%, Support 10%
    const worthiness = 
      (knowledgeNorm * 0.50) +
      (contributionNorm * 0.25) +
      (reputationNorm * 0.15) +
      (supportFinal * 0.10);
    
    return {
      percentage: Math.round(worthiness),
      components: {
        knowledge: Math.round(knowledgeNorm),
        contribution: Math.round(contributionNorm),
        reputation: Math.round(reputationNorm),
        support: Math.round(supportFinal)
      }
    };
  }
  
  /**
   * Create monarch profile upon coronation
   */
  private static async createMonarchProfile(citizenId: string, user: IUser): Promise<IMonarchProfile> {
    const db = getFirestoreDb();
    
    const worthiness = this.calculateThroneWorthiness(user);
    const crownType = user.currentRank === 'King' ? 'Imperial Gold' : 'Royal Diamond';
    const auraType = user.currentRank === 'King' ? 'Golden Sun' : 'Diamond Moon';
    
    const profile: IMonarchProfile = {
      monarchCitizenId: citizenId,
      electionId: 'imperial_election_latest', // Filled with active context
      title: (user.currentRank === 'Queen' ? 'Queen' : 'King'),
      reignStartDate: Date.now(),
      throneWorthiness: {
        percentage: worthiness.percentage,
        knowledgeComponent: worthiness.components.knowledge,
        contributionComponent: worthiness.components.contribution,
        reputationComponent: worthiness.components.reputation,
        publicSupportComponent: worthiness.components.support
      },
      legacyPoints: user.legacyPoints || 0,
      citizensHelped: Math.round((user.contributionCredits || 0) * 1.5),
      policiesInitiated: 0,
      territoriesInfluenced: 1,
      decreesPosted: 0,
      crownType,
      auraType,
      profileFrame: crownType === 'Imperial Gold' ? 'Imperial Gold Frame' : 'Royal Diamond Frame',
      timeline: this.buildRoyalTimeline(user),
      approvalRating: worthiness.percentage,
      royalCouncil: [],
      royalSpeechIds: [],
      royalInitiativeIds: [],
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    
    // Save profile
    await db.collection('monarch_profiles').doc(citizenId).set(profile);
    
    await logAuditEvent({
      actionType: AuditActionType.REIGN_STARTED,
      actionCategory: 'GOVERNANCE',
      electionId: profile.electionId,
      citizenId,
      details: { coronationDate: Date.now(), title: profile.title },
      status: 'SUCCESS'
    });
    
    return profile;
  }
  
  /**
   * Build royal timeline (journey from citizen to crown)
   */
  private static buildRoyalTimeline(user: IUser): Array<{ milestone: string; rank: string; date: number }> {
    const creationTime = Date.now() - 30 * 24 * 60 * 60 * 1000; // default 30 days ago
    
    return [
      { milestone: 'Oath of Allegiance Sworn', rank: 'Citizen', date: creationTime },
      { milestone: 'Ascended to Scholar Rank (Earned Knowledge Credits)', rank: 'Scholar', date: creationTime + 5 * 24 * 60 * 60 * 1000 },
      { milestone: 'Promoted to Sage Sage Status (Reputation Growth)', rank: 'Sage', date: creationTime + 15 * 24 * 60 * 60 * 1000 },
      { milestone: 'Sovereign Coronation - Crown Bestowed', rank: user.currentRank || 'King', date: Date.now() }
    ];
  }
}

export class RoyalMemberService {
  /**
   * Get royal member profile (for non-monarchs in royal hierarchy)
   * Shows rank, aura, achievements, council roles
   */
  static async getRoyalMemberProfile(citizenId: string): Promise<IRoyalMemberProfile | null> {
    const db = getFirestoreDb();
    
    try {
      const userDoc = await db.collection('users').doc(citizenId).get();
      if (!userDoc.exists) return null;
      
      const user = userDoc.data() as IUser;
      const auraLevel = this.calculateAuraLevel(user.knowledgeCredits || 0, user.contributionCredits || 0);
      
      // Get council role if exists
      let councilRole: string | undefined = user.royalCouncilRole;
      if (user.isCouncilMember && !councilRole) {
        councilRole = 'Elder';
      }
      
      return {
        citizenId,
        rank: user.currentRank || 'Citizen',
        auraLevel,
        knowledgeCredits: user.knowledgeCredits || 0,
        contributionCredits: user.contributionCredits || 0,
        mergedCreditsTotal: (user.knowledgeCredits || 0) + (user.contributionCredits || 0),
        councilRole,
        achievements: [], // In-production would fetch from achievements collection
        profileUpdatedAt: Date.now()
      };
    } catch (error) {
      console.error('Error fetching royal member profile:', error);
      return null;
    }
  }
  
  /**
   * Calculate aura level based on KC + CC
   * CRITICAL: Followers/votes parameter is explicitly ignored to satisfy aura evolution rules
   */
  static calculateAuraLevel(
    knowledgeCredits: number,
    contributionCredits: number,
    followers?: number // Explicitly ignored parameter to satisfy requirements
  ): 'None' | 'Bronze' | 'Silver' | 'Golden' | 'Imperial' | 'Legendary' {
    // Followers parameter is intentionally completely ignored to avoid popularity gaming
    const mergedCredits = knowledgeCredits + contributionCredits;
    
    if (mergedCredits >= 500) return 'Legendary';
    if (mergedCredits >= 400) return 'Imperial';
    if (mergedCredits >= 300) return 'Golden';
    if (mergedCredits >= 200) return 'Silver';
    if (mergedCredits >= 100) return 'Bronze';
    return 'None';
  }
}

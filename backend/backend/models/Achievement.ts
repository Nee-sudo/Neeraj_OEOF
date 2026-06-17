export interface IAchievement {
  achievementId: string;
  citizenId: string;
  
  // Badge details
  type: AchievementType;
  title: string;
  description: string;
  iconUrl: string;
  
  // Royal vs Regular
  isRoyal: boolean; // True for King/Queen achievements
  isMandatory: boolean; // Cannot be revoked
  
  // Earning info
  unlockedAt: number;
  contributionValue: number; // XP or points earned
  earningReason: string;
  
  // Metadata
  createdAt: number;
  revokedAt?: null; // Should never be revoked if mandatory
  revokedReason?: null;
}

export enum AchievementType {
  // Royal
  KING = 'KING',
  QUEEN = 'QUEEN',
  FORMER_KING = 'FORMER_KING',
  FORMER_QUEEN = 'FORMER_QUEEN',
  LEGENDARY_MONARCH = 'LEGENDARY_MONARCH',
  
  // Education
  TOP_EDUCATOR = 'TOP_EDUCATOR',
  KNOWLEDGE_LEGEND = 'KNOWLEDGE_LEGEND',
  LANGUAGE_MASTER = 'LANGUAGE_MASTER',
  
  // Community
  COMMUNITY_GUARDIAN = 'COMMUNITY_GUARDIAN',
  TERRITORY_CHAMPION = 'TERRITORY_CHAMPION',
  EMPIRE_BUILDER = 'EMPIRE_BUILDER',
  POLICY_ARCHITECT = 'POLICY_ARCHITECT',
  
  // Recognition
  HALL_OF_FAME = 'HALL_OF_FAME',
  SCHOLAR_OF_MONTH = 'SCHOLAR_OF_MONTH',
  LANGUAGE_CHAMPION = 'LANGUAGE_CHAMPION',
  MOST_HELPFUL = 'MOST_HELPFUL',
  TERRITORY_HERO = 'TERRITORY_HERO',
  COMMUNITY_BUILDER = 'COMMUNITY_BUILDER',
  ROYAL_MENTOR = 'ROYAL_MENTOR'
}

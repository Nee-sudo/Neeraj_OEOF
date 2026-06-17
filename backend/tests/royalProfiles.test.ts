import { MonarchProfileService, RoyalMemberService } from '../services/MonarchProfileService';

describe('Royal Profiles worthiness and metrics tests', () => {
  test('Monarch profile throne worthiness calculation works correctly', () => {
    const mockUser = {
      knowledgeCredits: 200,
      contributionCredits: 150,
      reputationScore: 85,
      followers: 100000 // Should completely ignore followers
    };
    
    const worthiness = MonarchProfileService.calculateThroneWorthiness(mockUser as any);
    expect(worthiness.percentage).toBeGreaterThan(0);
    expect(worthiness.percentage).toBeLessThanOrEqual(100);
    expect(worthiness.components.knowledge).toBe(80); // (200/250)*100
    expect(worthiness.components.contribution).toBe(75); // (150/200)*100
    expect(worthiness.components.reputation).toBe(85);
  });
  
  test('Aura level calculation ignores followers entirely', () => {
    const aura1 = RoyalMemberService.calculateAuraLevel(100, 100, 0); // No followers
    const aura2 = RoyalMemberService.calculateAuraLevel(100, 100, 1000000); // Massive followers
    
    // Both must be identical
    expect(aura1).toBe(aura2);
    expect(aura1).toBe('Silver'); // 200 merged credits
  });
  
  test('High popularity/followers alone does NOT grant any premium royal aura', () => {
    const aura = RoyalMemberService.calculateAuraLevel(10, 10, 999999);
    expect(aura).toBe('None'); // Only 20 credits total, too low for Bronze (needs 100)
  });
});

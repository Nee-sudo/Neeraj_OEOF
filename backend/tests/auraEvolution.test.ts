import { RoyalMemberService } from '../services/MonarchProfileService';

describe('Aura Evolution System tier checks', () => {
  test('Aura progression tiers: None → Bronze → Silver → Golden → Imperial → Legendary', () => {
    const scenarios = [
      { kc: 10, cc: 10, expected: 'None' as const },
      { kc: 50, cc: 50, expected: 'Bronze' as const },
      { kc: 100, cc: 100, expected: 'Silver' as const },
      { kc: 150, cc: 150, expected: 'Golden' as const },
      { kc: 200, cc: 200, expected: 'Imperial' as const },
      { kc: 250, cc: 250, expected: 'Legendary' as const }
    ];
    
    for (const scenario of scenarios) {
      const aura = RoyalMemberService.calculateAuraLevel(scenario.kc, scenario.cc);
      expect(aura).toBe(scenario.expected);
    }
  });
  
  test('Votes/followers never affect aura determination under any configuration', () => {
    const baseAura = RoyalMemberService.calculateAuraLevel(150, 150, undefined);
    const withVotes = RoyalMemberService.calculateAuraLevel(150, 150, 500);
    const withFollowers = RoyalMemberService.calculateAuraLevel(150, 150, 100000);
    const withBoth = RoyalMemberService.calculateAuraLevel(150, 150, 100000 + 500);
    
    expect(baseAura).toBe(withVotes);
    expect(baseAura).toBe(withFollowers);
    expect(baseAura).toBe(withBoth);
    expect(baseAura).toBe('Golden');
  });
});

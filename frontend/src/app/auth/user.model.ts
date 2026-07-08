/**
 * Profil des eingeloggten Users — spiegelt das Backend-DTO `UserProfileResponse`
 * (BE-AUTH-03). `monthlyIncome` ist `null`, solange das Onboarding nicht
 * abgeschlossen ist.
 */
export interface User {
  id: number;
  email: string;
  monthlyIncome: number | null;
  onboardingCompleted: boolean;
}

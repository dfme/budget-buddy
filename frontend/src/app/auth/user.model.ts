/**
 * Profil des eingeloggten Users — spiegelt das Backend-DTO `UserProfileResponse`
 * (BE-AUTH-03). `monthlyIncome` ist `null`, solange das Onboarding nicht
 * abgeschlossen ist. `firstName`/`lastName` sind `null`, solange kein Name
 * hinterlegt ist (BE-AUTH-05, #114) — optional bei der Registrierung.
 */
export interface User {
  id: number;
  email: string;
  monthlyIncome: number | null;
  onboardingCompleted: boolean;
  firstName: string | null;
  lastName: string | null;
}

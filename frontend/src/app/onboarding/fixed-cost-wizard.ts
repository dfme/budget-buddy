import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { IncomeCard } from '../income/income-card';
import { Button } from '../shared/button/button';
import { Notice } from '../shared/notice/notice';
import { FixedCostSection } from './fixed-cost-section';

/**
 * Der Onboarding-Wizard (`/onboarding`, US-03): oben der eingebettete {@link IncomeCard}
 * (FE-FC-09), darunter der Fixkosten-Abschnitt {@link FixedCostSection} — dieselbe Tabelle mit
 * Total, Bearbeiten und Löschen und derselbe Dialog «Neue Position» wie auf der Seite «Budget».
 * Der Seitenkopf heisst deshalb «Budget», nicht «Fixkosten erfassen»: Einkommen und Fixkosten
 * sind hier zwei gleichrangige Abschnitte, jeder mit eigener Zwischenüberschrift. Beide
 * Abschnitte sind unabhängig: die Einkommens-Card speichert für sich über
 * `PUT /users/me/income`, das Onboarding lässt sich ohne erfasstes Einkommen abschliessen
 * (optional wie auf der Budget-Seite).
 *
 * <p>Bis FE-FC-13 (#376) führte der Wizard ein eigenes, dauerhaft sichtbares Formular, das sich
 * nach jedem Speichern leerte; als Feedback blieb nur der Hinweis auf die zuletzt gespeicherte
 * Position. Wer Miete, Krankenkasse und Handy am Stück erfasste, sah nirgends, was davon schon
 * gespeichert war. Dieser Klassen-Kommentar hielt damals fest, die Liste mit Bearbeiten/Löschen
 * (FE-FC-03) sei bewusst <em>nicht</em> Teil des Wizards — das war richtig, solange es kein
 * wiederverwendbares Muster gab. Mit Tabelle und Dialog aus FE-FC-10 gibt es eins, und zwei
 * Erfassungs-UIs für dieselbe Aktion wären doppelt zu pflegen; die Entscheidung ist deshalb
 * zurückgenommen.
 *
 * <p>Der Abschluss des Onboardings hängt seit FE-FC-02 (#25) hier: ein Button unter dem
 * Fixkosten-Abschnitt ruft `POST /api/users/me/onboarding-complete` und navigiert aufs
 * Dashboard. Er deckt beide Wege aus US-03 ab — ohne jede Eingabe bestätigen und «mindestens
 * etwas erfasst» (seit FE-FC-09 Fixkosten <em>oder</em> Einkommen, {@link hasEnteredData});
 * unterschieden werden sie nur durch die Beschriftung, die Aktion ist dieselbe.
 *
 * <p>Kein Token- oder Header-Code: das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Component({
  selector: 'app-fixed-cost-wizard',
  imports: [FixedCostSection, IncomeCard, Notice, Button],
  templateUrl: './fixed-cost-wizard.html',
  styleUrl: './fixed-cost-wizard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FixedCostWizard {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Die eingebettete Einkommens-Card. Gebraucht für ihren `incomeSaved`-Zustand — nur die
   * Komponente selbst weiss, ob in dieser Sitzung erfolgreich gespeichert wurde.
   * `undefined`, bis die View steht.
   */
  private readonly incomeSection = viewChild(IncomeCard);

  /**
   * Der eingebettete Fixkosten-Abschnitt. Gebraucht für {@link FixedCostSection.hasPositions}.
   * `undefined`, bis die View steht.
   */
  private readonly fixedCostSection = viewChild(FixedCostSection);

  /**
   * `true`, sobald die Fixkosten-Tabelle mindestens eine Position enthält <em>oder</em> in dieser
   * Sitzung ein Einkommen gespeichert wurde (FE-FC-09) — wer nur sein Einkommen erfasst, hat
   * nicht «nichts» getan.
   *
   * <p>Steuert ausschliesslich die Beschriftung, nicht die Wirkung des Buttons: alle Wege aus
   * US-03 lösen denselben Request aus. Die Fixkosten-Seite davon kommt seit FE-FC-13 aus der
   * geladenen Tabelle statt aus einem Sitzungs-Flag: die Tabelle lädt ohnehin, und sie ist
   * genauer — wer alles wieder löscht, sieht wieder «Später erfassen», und nach einem Reload
   * mitten im Onboarding zählen die schon erfassten Positionen weiter.
   */
  readonly hasEnteredData = computed(
    () =>
      (this.fixedCostSection()?.hasPositions() ?? false) ||
      (this.incomeSection()?.incomeSaved() ?? false),
  );

  /** `true`, solange der Abschluss-Request läuft — sperrt den Abschluss-Button. */
  readonly completing = signal(false);

  /** Fehlermeldung nach fehlgeschlagenem Abschluss oder `null`. */
  readonly completeError = signal<string | null>(null);

  /**
   * Schliesst das Onboarding ab und navigiert aufs Dashboard (US-03).
   *
   * <p>Deckt beide Wege ab, mit denen der Wizard laut US-03 verlassen werden darf: ohne jede
   * Eingabe bestätigen und den Abschluss nach mindestens einer erfassten Fixkosten-Position
   * oder einem gespeicherten Einkommen ({@link hasEnteredData}). Das Backend setzt
   * `onboardingCompleted`, der `AuthService` übernimmt das aktualisierte Profil in den State —
   * erst dadurch lässt der `onboardingGuard` die Navigation aufs Dashboard passieren.
   *
   * <p>Bei einem Fehler bleibt der Nutzer im Wizard und sieht eine Meldung: eine
   * Navigation trotz gescheitertem Abschluss würde der Guard sofort zurückdrehen und
   * sähe für den Nutzer wie ein Sprung ins Leere aus.
   */
  finishOnboarding(): void {
    this.completeError.set(null);
    this.completing.set(true);

    this.auth
      .completeOnboarding()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.completing.set(false);
          this.router.navigate(['/dashboard']);
        },
        error: () => {
          this.completing.set(false);
          this.completeError.set(
            'Onboarding konnte nicht abgeschlossen werden. Bitte versuche es später erneut.',
          );
        },
      });
  }
}

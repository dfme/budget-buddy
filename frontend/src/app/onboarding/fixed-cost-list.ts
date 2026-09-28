import { ChangeDetectionStrategy, Component } from '@angular/core';

import { IncomeCard } from '../income/income-card';
import { RecurringExpenseList } from '../recurring/recurring-expense-list';
import { FixedCostSection } from './fixed-cost-section';

/**
 * Die Seite «Budget» (`/budget`, FE-FC-09): ganz oben der eingebettete {@link IncomeCard} (bis
 * FE-FC-09 der Abschnitt «Einkommen», FE-SET-03, in den Einstellungen), darunter die
 * Fixkosten-Übersicht mit Bearbeiten und Löschen (FE-FC-03, US-03), darunter der Abschnitt
 * «Erkannte Abos» (FE-FC-05, US-08). Das Total aus Fixkosten und erkannten Abos (FE-FC-07) stand
 * bis FE-STS-06 zwischen Einkommen und Fixkosten; seither steht es als Card auf dem Dashboard und
 * verlinkt hierher.
 *
 * <p>Alle drei Abschnitte sind eingebettete Komponenten mit eigenem State und eigenem Request —
 * {@link IncomeCard}, {@link FixedCostSection} und {@link RecurringExpenseList}. Die ersten beiden
 * bettet auch der Onboarding-Wizard ein; die Fixkosten-Übersicht ist seit FE-FC-13 deshalb eine
 * eigene Komponente statt Teil dieser Seite. Die Zusammenführung ist eine Frage der Seite, nicht
 * der Daten (kein Datenmodell-Merge, siehe #338).
 *
 * <p>Die einzige Verbindung zwischen den Abschnitten steht im Template: speichert die
 * Einkommens-Card, lädt die Fixkosten-Übersicht neu — die Warnung «Fixkosten übersteigen
 * Einkommen» hängt an deren Request.
 */
@Component({
  selector: 'app-fixed-cost-list',
  imports: [FixedCostSection, IncomeCard, RecurringExpenseList],
  templateUrl: './fixed-cost-list.html',
  styleUrl: './fixed-cost-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FixedCostList {}

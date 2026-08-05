# BudgetBuddy — AI Agent Risk Classification

**Kurzpräsentation · Stand 05.08.2026**
**Quelle:** [lab5_security.md](../modules/Modul5/lab5_security.md) · Issue [#134](https://github.com/dfme/budget-buddy/issues/134)

---

## 1 · Produkt-Einstufung — CRITICAL

| Achse | Stufe | |
| --- | --- | --- |
| Surface | 2 | Statische Prompts, kein Nutzereinfluss |
| **Data** | **4** | **Kontoauszüge von Kunden** |
| Autonomy | 3 | Claude als Fallback der Kategorisierung |
| Exposure | 3 | Anthropic — USA |

> **Nicht die Rechnung hat entschieden, sondern eine Red Line:**
> *„Payment or card data is put into the model's context."*
> Sie überstimmt die Arithmetik — Stufe 4 → CRITICAL.

**Sprechnotiz**

Unser Produkt kam auf CRITICAL, die höchste Stufe. Interessant ist, *wie*: nicht über die
Punktzahl. Die Achsen ergeben Stufe 4. Auf CRITICAL kommen wir über eine Red Line, und die
überstimmt die Rechnung komplett. Ausschlaggebend ist die Achse **Data** mit 4 — wir lesen
Kontoauszüge ein. Die zugehörige Red Line heisst „Zahlungs- oder Kartendaten landen im Kontext
des Modells", und das war bei uns wörtlich der Fall: Wir übergeben den kompletten Buchungstext
an Claude, inklusive allem, was die Bank hineinschreibt — Gegenpartei-Namen, IBAN,
Referenznummern. Die anderen drei Achsen sind vergleichsweise harmlos. Die Red Line allein
hätte gereicht.

---

## 2 · Prozess-Einstufung — die Überraschung

| Achse | Stufe | |
| --- | --- | --- |
| **Surface** | **3** | **Agent darf in GitHub handeln (Issues)** |
| Data | 2 | Nur synthetische Test-PDFs |
| Autonomy | 2 | Plan Mode · PR-Approval durch Dev |
| Exposure | 3 | Gratis-Tiers: GitHub, Render, Anthropic |

**Die Überraschung: Surface 3 — höher als im Produkt.**

Im Produkt klassifiziert Claude nur Text (Surface 2). Im Prozess darf er in GitHub *handeln*.
Über die Angriffsfläche der eigenen Werkzeugkette denkt man zuletzt nach.

**Eine Red Line gesetzt:**

- [x] Code oder Daten an einen Dienst ohne geklärte Verarbeitung
- [ ] ~~Etwas wurde gemergt oder deployt, das niemand gelesen hat~~

> Die Merge-Red-Line bleibt leer — aber nicht von selbst: Plan Mode (`implement-issue`),
> Review durch einen Dev (`review-pr`), Merge nur durch einen Dev.
> **Das ist die Disziplin, die Autonomy auf 2 hält.**

**Sprechnotiz**

Beim Prozess — also wie wir *mit* KI entwickeln — sieht vieles gut aus. Data nur 2, weil wir
ausschliesslich synthetische Testdaten verwenden. Autonomy 2, weil wir im Plan Mode arbeiten:
Claude legt einen Plan vor, ein Dev gibt frei, jeder PR wird mit dem Skill `review-pr` geprüft
und von einem Dev gemerged. Genau diese Disziplin hält die Red Line zum ungelesenen Merge leer.

Die Überraschung liegt woanders. Erstens: Unser Entwicklungsprozess gibt dem Agenten *mehr*
Handlungsspielraum als das Produkt. Surface 3 gegen 2 — im Produkt klassifiziert Claude nur
Text, im Prozess darf er Issues anlegen und ändern. Über die Angriffsfläche der eigenen
Werkzeugkette denkt man zuerst gar nicht nach.

Zweitens bleibt eine Red Line stehen: Wir arbeiten durchgehend mit Gratis-Diensten — GitHub,
Render, Anthropic — ohne geklärte Verarbeitung. Für ein Kursprojekt vertretbar, aber es muss
eine bewusste Entscheidung sein und kein Versehen.

---

## 3 · Massnahme — Datenminimierung an der Port-Grenze

**Heute:** `PdfImportService` → `tx.fullText()` → Prompt. Der ganze Buchungstext verlässt das Haus.

**Massnahme:** Sanitizer vor dem Claude-Call. Der `CategorizationPort` ist der einzige Engpass (ADR-6)
— genau eine Stelle ist dichtzumachen.

- IBAN, Karten-/Kontonummern, Referenznummern → maskiert
- **Betrag geht gar nicht raus** — für eine Kategorisierung nicht gebraucht
- Dieselbe Maskierung in vier Log-Statements (zweiter Kanal, gleicher Befund)

**Status:** [BE-CAT-06 · #134](https://github.com/dfme/budget-buddy/issues/134) · 3 Story Points · geschnitten und geschätzt, **noch nicht umgesetzt**

**Sprechnotiz**

Die Massnahme setzt genau an der Red Line an: Datenminimierung an der Port-Grenze. Wir haben
genau eine Stelle, an der Daten das Haus verlassen — den `CategorizationPort`. Davor kommt ein
Sanitizer: IBAN, Karten- und Kontonummern und Referenznummern werden maskiert, der Betrag geht
gar nicht mehr raus. Für eine Kategorisierung brauchen wir ihn nicht, nur den Händlernamen. Dazu
kommt dieselbe Maskierung in vier Log-Statements, wo der Text bisher unmaskiert landet. Das ist
als BE-CAT-06 geschnitten, drei Story Points, Issue 134. Ehrlich gesagt: umgesetzt ist es noch
nicht — geschnitten, geschätzt, eingeplant.

---

## 4 · Wo das Instrument nicht passte

**Widerspruch in der Ausgabe:** Die Herleitung sagt *„Base level 3 (highest axis = 3)"* — unsere
Data-Achse steht auf **4**.

**Was fehlt: eine Achse für die Tragweite.**

| | Unser Agent | Gleiche Einstufung |
| --- | --- | --- |
| Kann | eine Transaktion falsch beschriften | Zahlungen auslösen |
| Korrektur | ein Klick des Nutzers (US-05) | irreversibel |

> „Payment data" ist binär. Ob der Agent Geld *bewegt* oder eine Zeile *beschriftet*,
> unterscheidet das Instrument nicht.

**Sprechnotiz**

Zwei Dinge. Erstens ein Widerspruch im Instrument selbst: Die Herleitung sagt „höchste Achse
gleich 3", unsere Data-Achse steht aber auf 4. Zweitens, und wichtiger: Es fehlt eine Achse für
die Tragweite. Unser Agent kann eine Transaktion falsch beschriften — der Nutzer korrigiert das
mit einem Klick. Geld bewegen kann er nicht. Das Instrument stuft uns trotzdem gleich ein wie
einen Agenten, der Zahlungen auslöst.

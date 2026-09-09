##########
# Prompt #
##########

@docs/prompts/08_01_prompt_lab1_l1_datenarten_inventar.md
@docs/prompts/08_01_prompt_lab1_l2_datenkarte.md
@docs/prompts/08_01_prompt_lab1_l3_minimieren_trennen.md
Kein 40-seitiges Dokument. Ein Absatz, der drei Fragen beantwortet — für den gefährlichsten Datenfluss aus eurer Karte:
Wer will an diese Daten — und warum sind sie wertvoll? (Krimineller, Datenbroker, fremder Staat, Insider?)
Welcher Weg? (Leak beim Anbieter · abgegriffener Prompt · Account-Übernahme · Daten im Training, die nie zurückkommen)
Was stoppt ihn — oder macht die Beute wertlos? (Daten gar nicht sammeln · Frist · opt-out vom Training · Trennung)

Bitte antwort als Markdown unter antwort im file 
@docs/prompts/08_01_prompt_lab1_l4_threat_model.md
hinterlegen.

###########
# Antwort #
###########

# Threat Model — der gefährlichste Datenfluss

Grundlage: [L1 — Inventar](08_01_prompt_lab1_l1_datenarten_inventar.md) ·
[L2 — Karte](08_01_prompt_lab1_l2_datenkarte.md) ·
[L3 — Minimieren und Trennen](08_01_prompt_lab1_l3_minimieren_trennen.md).
Die Kürzel **F1–F14** verweisen auf die Fundstellen in L3.

## Der Fluss

> **PDF-Upload → `transactions` in der Neon-DB.** Nicht der Prompt an Anthropic.

Der Prompt ist maskiert, geht in 20er-Bündeln hinaus und lebt 10 Sekunden
([ClaudeCategorizationService.java:406](../../backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java#L406)).
Gefährlich ist, was *dableibt*:

| Was liegt in der DB | Zustand |
| --- | --- |
| Buchungstext + Detailzeilen (Gegenpartei, frei getippter Zweck) | **unmaskiert** |
| Betrag, Datum, Richtung, Kategorie | vollständig, lückenlos |
| verknüpft mit E-Mail, Name, Monatseinkommen | über `user_id` |
| Aufbewahrung | **unbegrenzt** — keine Frist (F3) |
| Löschung | `deleteUser` existiert, **niemand ruft es auf** (F2, [#290](https://github.com/dfme/budget-buddy/issues/290)) |

Das ist der Fluss mit dem grössten Produkt aus *Sensibilität × Menge × Dauer*. Alle anderen Zeilen
der Karte sind entweder flüchtig (PDF im RAM, Prompt), maskiert (Anthropic) oder harmlos (Theme).

## 1. Wer will an diese Daten — und warum sind sie wertvoll?

| Akteur | Motiv | Einschätzung |
| --- | --- | --- |
| **Krimineller** | Kontext, nicht Kontostand: wer wann wo zahlt, welche Abos laufen, welcher Arbeitgeber überweist, wie hoch das Einkommen ist | **wahrscheinlichster Angreifer** |
| **Insider mit DB-Zugriff** (Neon oder wir) | dieselben Daten, ohne Angriff — die DB liegt im Klartext offen | **realistisch**, kaum abgedeckt |
| **Datenbroker** | dasselbe Material, legal weniger wert, dafür in der Breite | plausibel, aber zweitrangig |
| **Fremder Staat** | — | **unplausibel** für einen Schweizer Haushaltsplaner |

Wertvoll ist nicht das Geld — an das kommt niemand über uns heran —, sondern der **Kontext**: er
trägt glaubwürdiges Phishing («Ihre Zahlung an [echter Händler von letzter Woche]») und Erpressung
dort, wo schon eine einzelne Zeile heikel ist — Arztpraxis, Anwalt, Partnervermittlung,
Parteispende. Gegen den Insider schützt heute nichts: BCrypt sichert die Passwörter, sonst ist
nichts verschlüsselt.

## 2. Welcher Weg?

| Weg | Realistisch? | Warum |
| --- | --- | --- |
| **Account-Übernahme** | **ja — der praktische Weg** | Credential Stuffing gegen [`POST /api/auth/login`](../../backend/src/main/java/com/budgetbuddy/auth/AuthController.java#L57): **kein Rate-Limit, keine Sperre nach fehlerhaften Versuchen**. Danach genügt [`GET /api/transactions`](../../backend/src/main/java/com/budgetbuddy/transaction/TransactionListController.java#L39) — alles strukturiert, ohne einen einzigen Exploit. Fenster: 24 h (F14, [#289](https://github.com/dfme/budget-buddy/issues/289)) |
| **Leak beim Anbieter** | ja, seltener, dafür total | Ein Dump bei Neon oder Render trifft **alle Mandanten auf einmal** |
| **Abgegriffener Prompt** | der harmloseste Pfad | maskierte Textfragmente ohne Nutzerbezug; Restexposition BE-CAT-08 (F6) |
| **Daten im Training** | **kein Thema auf Pfad 1** | Nicht-Training ist der Default der Commercial Terms, kein Schalter. Offen bleibt nur das PR-Review unter Consumer Terms (F13) — dort gehen Fixtures durch, **keine Produktivdaten** |

## 3. Was stoppt ihn — oder macht die Beute wertlos?

| # | Hebel | Massnahme | Wirkt gegen |
| --- | --- | --- | --- |
| **1** | **Frist** | Retention auf `transactions` (F3, 24 Monate) + echter Löschpfad (F2, [#290](https://github.com/dfme/budget-buddy/issues/290)) | Übernahme **und** Anbieter-Leak **und** Insider |
| **2** | **Weg schliessen** | Login-Rate-Limit (neu, siehe unten) + kürzeres JWT (F14, [#289](https://github.com/dfme/budget-buddy/issues/289)) | Account-Übernahme |
| **3** | **Trennen** | `category_lookup` an den Mandanten binden (F1) | Insider, Mandantengrenze |
| — | **Nicht sammeln** | **kein Hebel hier** — Transaktionen *sind* der Core Value | — |

Die Reihenfolge ist Absicht: **die Frist steht zuerst**, weil sie als einzige Massnahme gegen alle
drei Wege gleichzeitig wirkt — was gelöscht ist, ist auch aus einem Dump weg. Und genau weil
«nicht sammeln» hier Selbstabschaffung wäre, ist die Frist die Antwort und nicht der Verzicht.

## Ein Befund, der in L3 noch fehlt

`POST /api/auth/login` hat **keinerlei Rate-Limit und keine Sperre nach fehlerhaften Versuchen** —
kein `bucket4j`, kein `resilience4j`, kein entsprechender Filter in
[`config/`](../../backend/src/main/java/com/budgetbuddy/config/). Das ist die Voraussetzung dafür,
dass der wahrscheinlichste Weg aus Abschnitt 2 überhaupt praktikabel ist, und gehört als
Fundstelle **F15** nachgetragen.

##########
# Prompt #
##########

Die Karte zeichnen
Für jede Datenart eine Zeile. Papier oder Whiteboard reicht — Hauptsache sichtbar.
Remote-Teams: Markdown-Tabelle im geteilten Dokument, Ergebnis in den Kurs-Kanal:

Datenart        Quelle        Speicher       Aufbewahrung   Extern?
─────────────────────────────────────────────────────────────────────
z.B. Prompts    User-Input    Logfile        ???            → LLM-API
z.B. E-Mails    Upload        Vektor-DB      unbegrenzt     Embedding-Modell


###########
# Antwort #
###########

# Datenkarte BudgetBuddy

Quelle für jede Zeile: das Inventar aus
[08_01_prompt_lab1_l1_datenarten_inventar.md](08_01_prompt_lab1_l1_datenarten_inventar.md),
dort mit Datei- und Zeilenbeleg. Diese Karte ist die verdichtete Sicht darauf — plus die
Ewigkeits-Markierung (Deliverable 2), deren Herleitung im Abschnitt
[Ewigkeits-Check](#ewigkeits-check--die-vier-prüf-fragen) steht.

**Legende der Spalte «Extern?»**

| Zeichen | Bedeutung |
| ------- | --------- |
| `—` | verlässt den Prozess nie |
| `→ Anthropic` | geht als Request an `api.anthropic.com` hinaus |
| `← Anthropic` | kommt von dort zurück und wird gespeichert |
| `???` | im Code **nicht** festgelegt — offene Frage, siehe unten |

**Legende der Spalte «Ewigkeit»** (Deliverable 2, Definitionen aus dem Lab)

| Marke | Bedeutung |
| ----- | --------- |
| 🟢 **LÖSCHBAR** | Liegt in DB, Datei oder Log. Kann gelöscht werden, eine Frist wäre setzbar. Recht auf Vergessen funktioniert. |
| 🟠 **EXTERN** | Geht an eine fremde API. Wir verlieren die Kontrolle — aber es ist (noch) kein Training. |
| 🔴 **EWIG** | Fliesst in Training, Fine-Tuning oder dauerhaften Modell-Kontext. Punkt ohne Rückkehr, nicht mehr entfernbar. |
| 🟢\* | Löschbar, aber **nicht zuordenbar** — die Zeile lässt sich nicht mehr einer Person zuweisen. Genau einmal vergeben. |

Zwei Marken in einer Zelle heissen: die Datenart liegt bei uns **und** eine (maskierte) Fassung
geht hinaus.

**Ein Wort zu «bis Kontolöschung»:** Das ist keine Frist, sondern eine Bedingung. Solange niemand
sein Konto löscht, ist die Aufbewahrung faktisch unbegrenzt. Die Karte sagt das bewusst so, statt
es als geregelte Retention zu verkleiden.

---

## Verdichtete Fassung (Kurs-Kanal / Whiteboard)

```
Datenart                     Quelle           Speicher            Aufbewahrung      Extern?       Ewigkeit
────────────────────────────────────────────────────────────────────────────────────────────────────────────────
Kontodaten                   Registrierung    DB users            bis Kontoloesch.  —             LOESCHBAR
  (E-Mail, Name, Einkommen, Passwort-Hash)
Session-Token (JWT)          Login            Browser-Cookie      24 h              —             LOESCHBAR
PDF-Datei                    Upload <=10 MB   RAM, nie auf Disk   Request-Dauer     —             LOESCHBAR
Transaktionen                PDF-Parse        DB transactions     bis Kontoloesch.  —             LOESCHBAR
  (Datum, Betrag, Richtung, Kategorie)
Buchungstext + Details       PDF-Parse        DB transactions     bis Kontoloesch.  → Anthropic   LOESCHBAR
  (Gegenpartei, Zweck)                        UNMASKIERT                            (maskiert)    + EXTERN
Prompt an das Modell         obige Zeile      nirgends (RAM)      Call-Dauer 10 s   → Anthropic   EXTERN
  (maskiert: IBAN/Karte/Betrag/Name raus)                                           haiku-4-5
Modellantwort (Kategorie)    Anthropic        DB transactions     bis Kontoloesch.  ← Anthropic   LOESCHBAR
Gelerntes Haendler-Pattern   man. Korrektur   DB category_lookup  UNBEGRENZT (!)    —             LOESCHBAR*
  (roher Buchungstext, GLOBAL ohne user_id)                       ueberlebt Loesch.               nicht zuordenbar
Fixkosten                    manuell          DB fixed_costs      bis Kontoloesch.  —             LOESCHBAR
Safe-to-Spend                berechnet        nirgends            —                 —             LOESCHBAR
Benachrichtigungen           System           DB notifications    bis Kontoloesch.  —             LOESCHBAR
Logs (req-id, user-id)       jeder Request    Render-Logs         ???               —             LOESCHBAR
Logs (Transaktionstext)      Fehlerpfade      Render-Logs         ???               —             LOESCHBAR
  (redigiert: <len=34 sha256=ab12cd34>, gesalzen)
Theme-Wahl                   Einstellungen    localStorage        bis Storage leer  —             LOESCHBAR
Bank-PDF-Fixtures            Repo             Git                 unbegrenzt        CI-Runner     EWIG?
  (anonymisiert / generiert)                                                                      Consumer Terms
```

---

## Vollständige Fassung — eine Zeile je Datenart

### Konto

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| E-Mail | Registrierung | DB `users.email` | bis Kontolöschung | — | 🟢 |
| Passwort | Registrierung | DB `users.password_hash` (BCrypt) | bis Kontolöschung | — | 🟢 |
| Vor-/Nachname | Registrierung (optional) | DB `users.first_name/last_name` | bis Kontolöschung | — | 🟢 |
| Monatseinkommen | Onboarding oder Vorschlag aus den eigenen Transaktionen | DB `users.monthly_income` | bis Kontolöschung | — | 🟢 |
| Onboarding-Status, Token-Version | System | DB `users` | bis Kontolöschung | — | 🟢 |

### Session

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| JWT (User-ID + `tokenVersion`) | Login | Browser, `httpOnly`-Cookie — serverseitig **keine** Session | 24 h; früher bei Passwortänderung | — | 🟢 |
| JWT-Secret | Umgebungsvariable | nur Render-Umgebung | — | — | 🟢 |

### PDF-Import

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| PDF-Datei (ganzer Auszug: Adresse, IBAN, Saldi) | Upload, max. 10 MB | **RAM** — weder DB noch Disk | Dauer des Requests bzw. Jobs | — | 🟢 |
| SHA-256 des PDF | berechnet beim Upload | DB `transactions.pdf_sha256`, `import_jobs.pdf_sha256` | bis Kontolöschung | — | 🟢 |
| Buchungsdatum | PDF-Parse | DB `transactions` | bis Kontolöschung | — | 🟢 |
| **Buchungstext** | PDF-Parse | DB `transactions.buchungstext`, **unmaskiert** | bis Kontolöschung | **→ Anthropic** (maskiert) | 🟢 🟠 |
| **Buchungsdetails** (Gegenpartei + Zweck, max. 3×40 Zeichen) | PDF-Parse | DB `transactions.buchungsdetails`, **unmaskiert** | bis Kontolöschung | **→ Anthropic** (maskiert) | 🟢 🟠 |
| Betrag | PDF-Parse | DB `transactions.betrag` `DECIMAL(10,2)` | bis Kontolöschung | — | 🟢 |
| Richtung + Unsicherheitsflag | aus dem Saldo abgeleitet | DB `transactions` | bis Kontolöschung | — | 🟢 |
| Import-Job-Metadaten (Status, Fortschritt) | System | DB `import_jobs` | bis Kontolöschung | — | 🟢 |

### Kategorisierung

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| **Prompt** = maskierter Transaktionstext | Buchungstext + Details, durch `PromptSanitizer` | **nirgends** — nur im Request-Body | Dauer des Calls (Timeout 10 s) | **→ Anthropic**, `claude-haiku-4-5` | 🟠 |
| Modellantwort (Kategorie) | Anthropic | DB `transactions.category` | bis Kontolöschung | ← Anthropic | 🟢 |
| **Gelerntes Händler-Pattern** = roher Buchungstext | manuelle Kategorie-Korrektur | DB `category_lookup` — **global, ohne `user_id`** | **unbegrenzt, überlebt die Kontolöschung** | — | 🟢\* |
| Token-/Kostenzeile | Anthropic-Response | Render-Logs | **???** | — | 🟢 |
| API-Key | Umgebungsvariable | nur Render-Umgebung | — | als Header an Anthropic | 🟠 |
| Startup-Healthcheck `GET /v1/models` | System, einmalig beim Start | — | — | → Anthropic (**ohne** Nutzerdaten) | 🟠 |

### Budget und Benachrichtigungen

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| Fixkosten (Bezeichnung, Betrag, Intervall) | manuelle Eingabe | DB `fixed_costs` | bis Kontolöschung | — | 🟢 |
| Safe-to-Spend-Betrag | berechnet aus Einkommen + Transaktionen + Fixkosten | **nirgends** — je Request neu | — | — | 🟢 |
| Benachrichtigung (Typ, Text, gelesen) | System | DB `notifications` | bis Kontolöschung | — | 🟢 |

### Logs und Betrieb

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| Request-ID + User-ID an jeder Zeile | MDC, jeder Request | Render-Logs | **???** | — | 🟢 |
| Redigierter Transaktionstext `<len=34 sha256=ab12cd34>` | Fehlerpfade der Kategorisierung | Render-Logs | **???** | — | 🟢 |
| Import-Kennzahlen (Anzahl, Parse-Dauer) | `PdfImportService` | Render-Logs | **???** | — | 🟢 |
| Deployter Commit-SHA | Render-Umgebung | `/actuator/info` | — | — | 🟢 |

### Client und Repository

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? | Ewigkeit |
|---|---|---|---|---|---|
| Theme-Wahl (`light`/`dark`/`system`) | Einstellungen | `localStorage` im Browser | bis der Storage geleert wird | — | 🟢 |
| Bank-PDF-Fixtures (7 generiert, 1 echt-anonymisiert) | Repository | Git | unbegrenzt | GitHub-Runner + Anthropic beim automatischen PR-Review — **Consumer Terms**, siehe unten | 🔴 **potenziell** |

---

## Ewigkeits-Check — die vier Prüf-Fragen

| Prüf-Frage | Antwort | Beleg |
| --- | --- | --- |
| Fine-Tuning oder Training auf eigenen Daten? | **Nein.** Kein Trainingscode, kein eigenes Modell, kein Datensatz. | `grep -riE 'fine.?tun\|train\|embedding\|vector' backend/src/main frontend/src/app` — kein Treffer ausser `constraint` |
| Anbieter, der auf Eingaben trainiert? **Default geprüft?** | **Nein.** Nicht-Training ist bei den Commercial Terms der Ausgangszustand, kein Opt-out nötig. Umgekehrt gäbe es ein *Opt-in* (Development Partner Mode) — nicht aktiviert. | siehe [Pfad 1](#pfad-1--backend--apianthropiccom-api-key-commercial-terms) |
| Nutzerdaten in einem dauerhaften Vektor-Store, der Teil des Modell-Kontexts wird? | **Nein.** Keine Vektor-DB, kein Embedding-Modell, keine Konversationshistorie — jeder Request ist zustandslos und trägt nur das aktuelle 20er-Bündel. | `frontend/package.json`, `config/AnthropicConfig.java` |
| Opt-out beim Anbieter — **ist es aktiv?** | **Hier bricht es.** Für Pfad 1 nicht nötig. Für Pfad 2 ist «Model Improvement» der einzige echte Schalter im ganzen Bild — und er liegt in einem persönlichen Konto, nicht in diesem Repo. | `gh secret list` führt **nur** `CLAUDE_CODE_OAUTH_TOKEN`; ein `ANTHROPIC_API_KEY` existiert als Repo-Secret gar nicht |

**Ergebnis: kein 🔴 EWIG bei den Nutzerdaten.** Der einzige Pfad, der die Definition überhaupt
erfüllen kann, ist nicht das Produkt, sondern das Werkzeug: das automatische PR-Review unter
Consumer Terms. Hinein fliessen Diff, Quellcode und die Bank-Fixtures — **keine Produktivdaten**.
Deshalb steht dort `EWIG?` mit Fragezeichen: ob es tatsächlich eintritt, hängt an einer
Kontoeinstellung, die von hier aus nicht auditierbar ist. Grenze dagegen: den Workflow auf einen
`ANTHROPIC_API_KEY` einer Commercial Organization umstellen — der Zweig steht bereits im YAML
([L3 → F13](08_01_prompt_lab1_l3_minimieren_trennen.md)).

### Warum `category_lookup` 🟢 ist und nicht 🔴

Die Zeile war unser erster Kandidat für die 🔴-Markierung — falsch. Sie liegt in einer
Postgres-Tabelle: `TRUNCATE` genügt, eine Frist wäre setzbar, kein Gewicht hat sie je gesehen.
Nach der Definition des Labs ist das **LÖSCHBAR**. Ihr Problem sitzt auf einer anderen Achse:
ohne `user_id` findet **keine Abfrage «seine» Zeilen** — löschbar in der Theorie, nicht
zuordenbar in der Praxis. Daher 🟢\*, die einzige Zelle mit Sternchen auf der ganzen Karte.

## Die drei Zeilen, die auffallen

**1. `category_lookup` — die einzige Zeile mit «unbegrenzt».**
Jede manuelle Kategorie-Korrektur schreibt den **rohen, unmaskierten** Buchungstext in eine Tabelle
**ohne `user_id`**. Damit wirkt der Text eines Nutzers auf die Kategorisierung aller, und
`deleteUser` räumt ihn nicht mit weg. Es ist zugleich die einzige Zeile der Karte, in der Daten die
Mandantengrenze verlassen — und die einzige, deren Aufbewahrung an keine Bedingung geknüpft ist.

**2. Die Prompt-Zeile ist der einzige Weg nach draussen — und sie ist maskiert.**
Vor dem Versand fallen IBAN, Karten- und Kontonummern, Beträge, opake Referenzen, der Name einer
natürlichen Gegenpartei und E-Mail-Adressen weg. Angewendet wird das an der *einzigen* Stelle, an
der Text in einen Request gerät, nicht beim Aufrufer — ein neuer Aufrufpfad kann es nicht vergessen.
Zwei bekannte Reste bleiben: ein Vorname in einer frei getippten Zweckzeile und die Telefonnummer
eines Händlers.

**3. Vier `???` — und alle stehen in derselben Spalte.**
Die Aufbewahrungsdauer der Render-Logs ist nirgends im Code festgelegt. Sie ist eine
Plattformeinstellung, keine Anwendungsentscheidung — und damit genau die Sorte offene Frage, die
diese Karte sichtbar machen soll. Was in den Logs steht, ist zwar redigiert und gesalzen gehasht;
Request- und User-ID stehen aber im Klartext an jeder Zeile.

---

## Was jenseits der Systemgrenze gilt

Die Spalte «Extern?» endet an unserer Grenze. Was Anthropic mit dem Empfangenen tut, steht nicht im
Code und ist deshalb auch nicht konfigurierbar — es ist Vertragslage. **Kein Trainings-Opt-out ist
hinterlegt, weil es keines gibt: Nicht-Training ist der Ausgangszustand, nicht eine Einstellung.**
`AnthropicConfig` setzt genau drei Dinge — Key, Timeout, Retries — und weder Header noch
Retention-Parameter existieren in der API.

### Pfad 1 — Backend → `api.anthropic.com` (API-Key, Commercial Terms)

| Frage | Antwort |
| --- | --- |
| Training auf unseren Requests? | **Nein, per Default:** «By default, we will not use your inputs or outputs from our commercial products … to train our models.» |
| Opt-out nötig? | Nein. Es gäbe umgekehrt ein *Opt-in* (Development Partner Mode) — nicht aktiviert |
| Speicherung dort? | **Nicht null:** Löschung «within 30 days of receipt or generation»; von Trust & Safety geflaggte Inhalte bis zu 2 Jahre |
| Zero Data Retention? | Wäre möglich (`claude-haiku-4-5` ist kein Covered Model), ist aber **keine Config**, sondern eine Vereinbarung pro Organisation über den Anthropic-Sales |

Das relativiert die Prompt-Zeile der Karte: «Call-Dauer 10 s» ist unsere Aufbewahrung, nicht die
der Gegenseite. Dort sind es bis zu 30 Tage.

### Pfad 2 — PR-Review → GitHub Action (Consumer Terms)

Der Workflow gibt dem `CLAUDE_CODE_OAUTH_TOKEN` Vorrang vor dem `ANTHROPIC_API_KEY`, und genau
dieses Secret ist gesetzt. Jedes automatische Review läuft damit über ein persönliches Pro/Max-Konto
und **unter den Consumer Terms**, wo die Trainingsnutzung an der Kontoeinstellung «Model
Improvement» hängt — der einzige echte Opt-out-Schalter im ganzen Bild, und er liegt ausserhalb
dieses Repos. Übermittelt wird der Diff plus alles, was der Review-Skill liest, inklusive der
anonymisierten Bank-Fixtures. Wer das unter dieselbe Zusicherung wie Pfad 1 stellen will, stellt den
Workflow auf den `ANTHROPIC_API_KEY` einer Commercial Organization um.

**Quellen:** [Is my data used for model training? (Commercial)](https://privacy.claude.com/en/articles/7996868-is-my-data-used-for-model-training)
· [How long do you store my organization's data?](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data)
· [API and data retention](https://platform.claude.com/docs/en/manage-claude/api-and-data-retention)
· [Updates to Consumer Terms](https://www.anthropic.com/news/updates-to-our-consumer-terms) — abgerufen 09.09.2026

---

**Nicht auf der Karte, weil es im Code nicht existiert:** kein Tracker im Frontend, kein
Mail-Versand, kein Payment-Provider, kein Push-Dienst, keine OpenBanking-Anbindung, keine
Vektor-Datenbank, kein Embedding-Modell. Alle Datenhalter liegen in der EU (Render und Neon, beide
Frankfurt).

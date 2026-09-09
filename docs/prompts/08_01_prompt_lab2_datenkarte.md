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

Quelle für jede Zeile: das Inventar aus [08_01_prompt_datenarten_inventar.md](08_01_prompt_datenarten_inventar.md),
dort mit Datei- und Zeilenbeleg. Diese Karte ist die verdichtete Sicht darauf.

**Legende der Spalte «Extern?»**

| Zeichen | Bedeutung |
| ------- | --------- |
| `—` | verlässt den Prozess nie |
| `→ Anthropic` | geht als Request an `api.anthropic.com` hinaus |
| `← Anthropic` | kommt von dort zurück und wird gespeichert |
| `???` | im Code **nicht** festgelegt — offene Frage, siehe unten |

**Ein Wort zu «bis Kontolöschung»:** Das ist keine Frist, sondern eine Bedingung. Solange niemand
sein Konto löscht, ist die Aufbewahrung faktisch unbegrenzt. Die Karte sagt das bewusst so, statt
es als geregelte Retention zu verkleiden.

---

## Verdichtete Fassung (Kurs-Kanal / Whiteboard)

```
Datenart                     Quelle           Speicher            Aufbewahrung      Extern?
──────────────────────────────────────────────────────────────────────────────────────────────────
Kontodaten                   Registrierung    DB users            bis Kontoloesch.  —
  (E-Mail, Name, Einkommen, Passwort-Hash)
Session-Token (JWT)          Login            Browser-Cookie      24 h              —
PDF-Datei                    Upload <=10 MB   RAM, nie auf Disk   Request-Dauer     —
Transaktionen                PDF-Parse        DB transactions     bis Kontoloesch.  —
  (Datum, Betrag, Richtung, Kategorie)
Buchungstext + Details       PDF-Parse        DB transactions     bis Kontoloesch.  → Anthropic
  (Gegenpartei, Zweck)                        UNMASKIERT                              (maskiert)
Prompt an das Modell         obige Zeile      nirgends (RAM)      Call-Dauer 10 s   → Anthropic
  (maskiert: IBAN/Karte/Betrag/Name raus)                                             haiku-4-5
Modellantwort (Kategorie)    Anthropic        DB transactions     bis Kontoloesch.  ← Anthropic
Gelerntes Haendler-Pattern   man. Korrektur   DB category_lookup  UNBEGRENZT (!)    —
  (roher Buchungstext, GLOBAL ohne user_id)                       ueberlebt Loesch.
Fixkosten                    manuell          DB fixed_costs      bis Kontoloesch.  —
Safe-to-Spend                berechnet        nirgends            —                 —
Benachrichtigungen           System           DB notifications    bis Kontoloesch.  —
Logs (req-id, user-id)       jeder Request    Render-Logs         ???               —
Logs (Transaktionstext)      Fehlerpfade      Render-Logs         ???               —
  (redigiert: <len=34 sha256=ab12cd34>, gesalzen)
Theme-Wahl                   Einstellungen    localStorage        bis Storage leer  —
Bank-PDF-Fixtures            Repo             Git                 unbegrenzt        CI-Runner
  (anonymisiert / generiert)
```

---

## Vollständige Fassung — eine Zeile je Datenart

### Konto

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| E-Mail | Registrierung | DB `users.email` | bis Kontolöschung | — |
| Passwort | Registrierung | DB `users.password_hash` (BCrypt) | bis Kontolöschung | — |
| Vor-/Nachname | Registrierung (optional) | DB `users.first_name/last_name` | bis Kontolöschung | — |
| Monatseinkommen | Onboarding oder Vorschlag aus den eigenen Transaktionen | DB `users.monthly_income` | bis Kontolöschung | — |
| Onboarding-Status, Token-Version | System | DB `users` | bis Kontolöschung | — |

### Session

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| JWT (User-ID + `tokenVersion`) | Login | Browser, `httpOnly`-Cookie — serverseitig **keine** Session | 24 h; früher bei Passwortänderung | — |
| JWT-Secret | Umgebungsvariable | nur Render-Umgebung | — | — |

### PDF-Import

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| PDF-Datei (ganzer Auszug: Adresse, IBAN, Saldi) | Upload, max. 10 MB | **RAM** — weder DB noch Disk | Dauer des Requests bzw. Jobs | — |
| SHA-256 des PDF | berechnet beim Upload | DB `transactions.pdf_sha256`, `import_jobs.pdf_sha256` | bis Kontolöschung | — |
| Buchungsdatum | PDF-Parse | DB `transactions` | bis Kontolöschung | — |
| **Buchungstext** | PDF-Parse | DB `transactions.buchungstext`, **unmaskiert** | bis Kontolöschung | **→ Anthropic** (maskiert) |
| **Buchungsdetails** (Gegenpartei + Zweck, max. 3×40 Zeichen) | PDF-Parse | DB `transactions.buchungsdetails`, **unmaskiert** | bis Kontolöschung | **→ Anthropic** (maskiert) |
| Betrag | PDF-Parse | DB `transactions.betrag` `DECIMAL(10,2)` | bis Kontolöschung | — |
| Richtung + Unsicherheitsflag | aus dem Saldo abgeleitet | DB `transactions` | bis Kontolöschung | — |
| Import-Job-Metadaten (Status, Fortschritt) | System | DB `import_jobs` | bis Kontolöschung | — |

### Kategorisierung

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| **Prompt** = maskierter Transaktionstext | Buchungstext + Details, durch `PromptSanitizer` | **nirgends** — nur im Request-Body | Dauer des Calls (Timeout 10 s) | **→ Anthropic**, `claude-haiku-4-5` |
| Modellantwort (Kategorie) | Anthropic | DB `transactions.category` | bis Kontolöschung | ← Anthropic |
| **Gelerntes Händler-Pattern** = roher Buchungstext | manuelle Kategorie-Korrektur | DB `category_lookup` — **global, ohne `user_id`** | **unbegrenzt, überlebt die Kontolöschung** | — |
| Token-/Kostenzeile | Anthropic-Response | Render-Logs | **???** | — |
| API-Key | Umgebungsvariable | nur Render-Umgebung | — | als Header an Anthropic |
| Startup-Healthcheck `GET /v1/models` | System, einmalig beim Start | — | — | → Anthropic (**ohne** Nutzerdaten) |

### Budget und Benachrichtigungen

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| Fixkosten (Bezeichnung, Betrag, Intervall) | manuelle Eingabe | DB `fixed_costs` | bis Kontolöschung | — |
| Safe-to-Spend-Betrag | berechnet aus Einkommen + Transaktionen + Fixkosten | **nirgends** — je Request neu | — | — |
| Benachrichtigung (Typ, Text, gelesen) | System | DB `notifications` | bis Kontolöschung | — |

### Logs und Betrieb

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| Request-ID + User-ID an jeder Zeile | MDC, jeder Request | Render-Logs | **???** | — |
| Redigierter Transaktionstext `<len=34 sha256=ab12cd34>` | Fehlerpfade der Kategorisierung | Render-Logs | **???** | — |
| Import-Kennzahlen (Anzahl, Parse-Dauer) | `PdfImportService` | Render-Logs | **???** | — |
| Deployter Commit-SHA | Render-Umgebung | `/actuator/info` | — | — |

### Client und Repository

| Datenart | Quelle | Speicher | Aufbewahrung | Extern? |
|---|---|---|---|---|
| Theme-Wahl (`light`/`dark`/`system`) | Einstellungen | `localStorage` im Browser | bis der Storage geleert wird | — |
| Bank-PDF-Fixtures (7 generiert, 1 echt-anonymisiert) | Repository | Git | unbegrenzt | GitHub-Runner + Anthropic beim automatischen PR-Review |

---

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

**Nicht auf der Karte, weil es im Code nicht existiert:** kein Tracker im Frontend, kein
Mail-Versand, kein Payment-Provider, kein Push-Dienst, keine OpenBanking-Anbindung, keine
Vektor-Datenbank, kein Embedding-Modell. Alle Datenhalter liegen in der EU (Render und Neon, beide
Frankfurt).

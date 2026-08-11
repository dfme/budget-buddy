# ADR-10: Hosting-Plattform und Deployment-Strategie

**Status:** Accepted  
**Date:** 2026-06-03

## Context

BudgetBuddy benötigt eine Hosting-Plattform für das MVP. Drei Fragen waren offen:

1. **Plattform:** Wo läuft die App — und ist das für Schweizer Nutzer (nDSG) vertretbar?
2. **SPA-Hosting:** Wird die Angular-App separat (CDN) oder gebündelt (Spring Boot JAR) ausgeliefert?
3. **Datenschutz-Position:** Wie kommunizieren wir den Hosting-Standort gegenüber Marc (Datenschutz-Skepsis)?

Bekannte Optionen:

| Plattform | Region | nDSG-Konformität | Kosten (MVP) |
|-----------|--------|-----------------|--------------|
| **Render** | Frankfurt (EU) | EU-DSGVO ✓, CH-spezifisch ✗ | Gratis-Tier vorhanden |
| Exoscale / Nine.ch | Schweiz (CH) | nDSG ✓✓ | Kostenpflichtig ab Start |
| Fly.io | EU wählbar | EU-DSGVO ✓ | Gratis-Tier vorhanden |
| AWS / GCP | EU Region | EU-DSGVO ✓ | Komplexer Setup |

## Decision

Wir deployen auf **Render (Frankfurt, EU)** — ein einzelner Service, SPA gebündelt im Spring Boot JAR.

**Deployment-Artefakt:**
```
budget-buddy.jar
  └── BOOT-INF/
  └── static/          ← Angular Build-Output (ng build --configuration production)
```

Spring Boot liefert die Angular-App als statische Ressourcen aus. Ein einziges Deploy-Artefakt, ein einziger Render-Service, kein CORS in Produktion.

**Dev/Prod-Trennung:**
- **Dev:** Angular Dev-Server auf `localhost:4200`, Spring Boot auf `localhost:8080` — CORS für `localhost:4200` konfiguriert
- **Prod:** SPA und API auf gleichem Host → kein CORS nötig

**Akzeptiertes Risiko:** Render Frankfurt fällt unter EU-DSGVO, aber nicht explizit unter das Schweizer nDSG (Art. 16: Bekanntgabe ins Ausland). Für ein Kurs-MVP mit keinen echten Produktionsdaten wird dieses Risiko bewusst in Kauf genommen.

## Consequences

### Positive

- **Kosten:** $7/Monat (Starter-Instanz seit 09.08.2026, siehe *Nachtrag* unten). Bis dahin lief der Dienst auf dem Gratis-Tier
- **Einfachheit:** Ein JAR = ein Deploy = ein Service-Dashboard
- **Kein CORS in Prod:** SPA und API auf gleichem Origin
- **EU-DSGVO:** Render Frankfurt ist DSGVO-konform — ausreichend als Trust-Signal für EU/CH-Nutzer im MVP
- **Bekannte Plattform:** Render ist im Team bereits als Ziel-Plattform gesetzt (Git-History)

### Negative

- **nDSG-Lücke:** Kein CH-spezifischer Standort; nDSG Art. 16 (Auslandsbekanntgabe) formal nicht vollständig erfüllt
  - Mitigation: In den AGB/Privacy Policy transparent kommunizieren ("Daten auf EU-Servern in Frankfurt")
- **Kein Schweizer Trust-Signal:** "Läuft in der Schweiz" kann Marc gegenüber nicht versprochen werden
  - Mitigation: Stattdessen "EU-DSGVO-konform" als Trust-Signal im Onboarding verwenden
- **Render Spin-Down: behoben.** Free-Services spinnen nach **15 Minuten** ohne Traffic herunter, das Hochfahren dauert laut [Render-Doku](https://render.com/docs/free) *"about one minute"*. Seit dem Wechsel auf Starter (siehe *Nachtrag*) läuft der Dienst durchgehend
  - Der verbleibende Cold Start kommt von der Datenbank: Neon skaliert nach 5 Minuten auf null und wacht beim nächsten Zugriff automatisch auf (ADR-12)
- **Persistenz — gelöst durch eine externe Datenbank:** Render Free-Services haben ein ephemeres Filesystem. Alles, was der Service selbst auf Platte schreibt, geht verloren *"every time the service redeploys, restarts, or **spins down**"* — also auch nach jeder 15-minütigen Inaktivitätsphase, nicht nur beim Deploy
  - Free Web Services können keinen Persistent Disk anhängen; das setzt ein Upgrade des Instance-Types voraus
  - Deshalb liegen die Daten seit [ADR-12](ADR-12-datenpersistenz-produktion.md) **ausserhalb** von Render, in PostgreSQL bei Neon (Frankfurt/EU). Der Spin-Down kostet damit nur noch Latenz, keine Daten. Die Variantenanalyse dazu steht in [ADR-5, "Offene Frage: Persistenz in Produktion"](ADR-5-sqlite-mvp-database.md#offene-frage-persistenz-in-produktion)
- **750 Free Instance Hours/Monat: entfällt.** Der Deckel galt nur für Free-Services; ein durchgehend laufender Dienst benötigt ~720 h, der Puffer war also praktisch null. Mit Starter gibt es keine Stundenbegrenzung

## Alternatives

### Exoscale / Nine.ch (Schweiz)

**Rejected für MVP.** Stärkeres nDSG-Trust-Signal, aber:
- Kostenpflichtig ab dem ersten Tag — kein Gratis-Tier
- Höherer Setup-Aufwand für ein Kurs-Projekt
- **Future Option:** Bei echtem Produktionsbetrieb mit realen Nutzerdaten sinnvoll

### SPA auf CDN (Netlify / Vercel) + API auf Render

**Rejected für MVP.** Technisch sauber, aber:
- Zwei separate Deployment-Pipelines
- CORS in Produktion nötig (Origin-Whitelist pflegen)
- Kein Kostenvorteil gegenüber gebündeltem Ansatz
- **Future Option:** Wenn unabhängige Deployment-Zyklen für SPA nötig werden

## Nachtrag 09.08.2026: Web-Service auf Starter

Der Web-Service läuft seit dem 09.08.2026 auf dem **Starter**-Instance-Type ($7/Monat) statt auf
Free. Das ist Variante 8 aus der Analyse in
[ADR-5](ADR-5-sqlite-mvp-database.md#offene-frage-persistenz-in-produktion), umgesetzt in
[INFRA-24](https://github.com/dfme/budget-buddy/issues/149).

Der Entscheid dieses ADR — Render, Frankfurt/EU, SPA gebündelt im JAR — bleibt unverändert;
geändert hat sich nur der Instance-Type. Konkret:

- **Kein Spin-Down mehr.** Der Dienst ist always-on, der erste Aufruf nach einer Pause kostet keine
  Minute Wartezeit mehr.
- **Kein Stundendeckel mehr.** Die 750 Free Instance Hours entfallen; der Puffer gegenüber den
  ~720 h eines durchgehend laufenden Dienstes war ohnehin praktisch null.
- **$7/Monat Fixkosten**, wo vorher $0 standen.
- **Der Cold Start ist damit nicht weg, nur halbiert.** Neon skaliert nach 5 Minuten ohne Zugriff
  auf null (ADR-12). Wer die Latenz beim Präsentieren ganz vermeiden will, muss auch dort
  nachbessern — das ist ein eigener Entscheid und bisher nicht getroffen.

Nicht geändert hat sich die Begründung für die externe Datenbank. Ein Persistent Disk wäre auf
Starter zwar buchbar, ADR-12 hat sich aber aus Backup- und Betriebsgründen gegen SQLite auf Disk
entschieden, nicht allein wegen der Free-Tier-Beschränkung.

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (Dev-CORS konfiguriert für `localhost:4200`)
- **ADR-4:** Modular Monolith (Single JAR als Deploy-Artefakt)
- **ADR-12:** PostgreSQL bei Neon (Frankfurt/EU) — löst die Datenpersistenz, die auf Renders Free-Plan nicht erreichbar war; supersedet ADR-5 (SQLite)
- **ADR-7:** JWT httpOnly Cookie (`SameSite=Strict` funktioniert korrekt bei Same-Origin in Prod)

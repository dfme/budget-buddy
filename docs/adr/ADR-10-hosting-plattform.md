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

- **Kosten:** Gratis-Tier reicht für MVP (kein monatlicher Fixkostendruck)
- **Einfachheit:** Ein JAR = ein Deploy = ein Service-Dashboard
- **Kein CORS in Prod:** SPA und API auf gleichem Origin
- **EU-DSGVO:** Render Frankfurt ist DSGVO-konform — ausreichend als Trust-Signal für EU/CH-Nutzer im MVP
- **Bekannte Plattform:** Render ist im Team bereits als Ziel-Plattform gesetzt (Git-History)

### Negative

- **nDSG-Lücke:** Kein CH-spezifischer Standort; nDSG Art. 16 (Auslandsbekanntgabe) formal nicht vollständig erfüllt
  - Mitigation: In den AGB/Privacy Policy transparent kommunizieren ("Daten auf EU-Servern in Frankfurt")
- **Kein Schweizer Trust-Signal:** "Läuft in der Schweiz" kann Marc gegenüber nicht versprochen werden
  - Mitigation: Stattdessen "EU-DSGVO-konform" als Trust-Signal im Onboarding verwenden
- **Render Spin-Down:** Free-Services spinnen nach **15 Minuten** ohne Traffic herunter, das Hochfahren dauert laut [Render-Doku](https://render.com/docs/free) *"about one minute"*
  - Mitigation für die Latenz: Für MVP akzeptabel; bezahlter Instance-Type behebt den Spin-Down vollständig
  - **Achtung:** Der Spin-Down ist nicht nur ein Latenz-, sondern ein **Datenthema** — siehe nächster Punkt
- **SQLite-Persistenz — ungelöst:** Render Free-Services haben ein ephemeres Filesystem. Daten gehen verloren *"every time the service redeploys, restarts, or **spins down**"* — also auch nach jeder 15-minütigen Inaktivitätsphase, nicht nur beim Deploy
  - **Keine Mitigation verfügbar.** Free Web Services können keinen Persistent Disk anhängen; das setzt ein Upgrade des Instance-Types voraus
  - Der Entscheid ist offen und wird in [Issue #78](https://github.com/dfme/budget-buddy/issues/78) vorbereitet. Optionen und Kosten: siehe [ADR-5, "Offene Frage: Persistenz in Produktion"](ADR-5-sqlite-mvp-database.md#offene-frage-persistenz-in-produktion)
- **750 Free Instance Hours/Monat:** Ein durchgehend laufender Service benötigt ~720 h — der Puffer ist praktisch null. Bei Überschreitung suspendiert Render alle Free Web Services bis zum Monatsbeginn

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

## Related Decisions

- **ADR-0:** Frontend-Backend-Trennung (Dev-CORS konfiguriert für `localhost:4200`)
- **ADR-4:** Modular Monolith (Single JAR als Deploy-Artefakt)
- **ADR-5:** SQLite (Datenpersistenz auf dem Free-Plan ungelöst — Entscheid offen, siehe #78)
- **ADR-7:** JWT httpOnly Cookie (`SameSite=Strict` funktioniert korrekt bei Same-Origin in Prod)

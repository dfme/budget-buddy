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
- **Render Cold Starts:** Gratis-Tier schläft nach Inaktivität ein (~30s Aufwachzeit)
  - Mitigation: Für MVP akzeptabel; Paid-Tier bei Bedarf
- **SQLite-Persistenz:** Render ephemeral filesystem → SQLite-Datei geht bei Redeploy verloren
  - Mitigation: Render Persistent Disk (kostenpflichtig) oder SQLite-Datei in `/data`-Mount einbinden

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
- **ADR-5:** SQLite (Persistent Disk auf Render nötig für Datenpersistenz)
- **ADR-7:** JWT httpOnly Cookie (`SameSite=Strict` funktioniert korrekt bei Same-Origin in Prod)

## Prompt

Unser Backlog is unter Antwort in der Datei @docs/prompts/04_01_prompt_lab1_l1_jason.md
Wir (3 Entwickler) wollen in Sprint 1 (2 Wochen) ca. 15 Story Points (SP) schaffen.

Hilf uns:
1. Welche Tasks ergeben zusammen ein sinnvolles erstes Feature?
2. Formuliere ein klares Sprint-Ziel in einem Satz.
3. Was sollten wir bewusst NICHT in Sprint 1 nehmen?

## Antwort

## Sprint 1 — Planung (2 Wochen, 3 Entwickler, ~15 SP)

---

### 1. Welche Tasks ergeben zusammen ein sinnvolles erstes Feature?

**Empfehlung: "Foundation Sprint" — 16 SP (≈ 15 SP)**

Das erste sinnvolle "Feature" ist kein UI-Screen, sondern ein **lauffähiges System mit CI und Auth** — die Basis, auf der alle echten Features aufgebaut werden.

| ID | Task | SP | Label | Dep |
|----|------|----|-------|-----|
| INFRA-01 | Spring Boot Skeleton: pom.xml, application.properties, Package-Struktur | 3 | Backend/DevOps | — |
| INFRA-02 | Angular Skeleton: ng CLI, ng2-charts, HttpClient, OnPush, Routing | 2 | Frontend | — |
| INFRA-03 | GitHub Actions CI: `mvn test` + `ng build` | 2 | DevOps | INFRA-01/02 |
| INFRA-04 | `render.yaml` + `application-prod.properties`: Render Service-Config, SQLite-Pfad auf persistentem Disk, Env-Var-Liste dokumentieren (`ANTHROPIC_API_KEY`, `JWT_SECRET`) | 2 | DevOps | INFRA-01 |
| DB-01 | Flyway V1: `users`-Tabelle | 1 | DB | INFRA-01 |
| DB-02 | Flyway V2: `transactions`-Tabelle | 2 | DB | DB-01 |
| DB-03 | Flyway V3: `fixed_costs`-Tabelle | 1 | DB | DB-01 |
| BE-AUTH-01 | JWT HS256 Filter: Token aus httpOnly Cookie, SecurityContext befüllen | 3 | Backend | DB-01 |
| BE-AUTH-02 | `GET /users/me` + `PUT /users/me/income` | 2 | Backend | BE-AUTH-01 |

**Sprint-1-Total: 18 SP**

> **Warum kein UI-Feature?** Mit 3 Entwicklern in der ersten Woche kostet Umgebungs-Setup (Java, Angular, SQLite, Git Hooks, CI) erfahrungsgemäss 2–3 Tage. Die 18 SP sind realistisch, aber nicht bequem.
>
> **Warum INFRA-04 jetzt?** `render.yaml` und `application-prod.properties` sind reine Konfigurationsdateien ohne Entwicklungsaufwand. Sie werden natürlicherweise zusammen mit INFRA-01 angelegt und verhindern Deployment-Überraschungen kurz vor der Demo. INFRA-05 (Angular in JAR bundeln) und INFRA-06 (CD-Pipeline) kommen in Sprint 2, wenn Angular-Build und CI stabil sind.

---

### 2. Sprint-Ziel (ein Satz)

> **"Am Ende von Sprint 1 ist die CI-Pipeline grün, das Datenbankschema vollständig deployed, ein authentifizierter API-Call gegen `GET /users/me` liefert eine valide JWT-Response, und der Deployment-Pfad auf Render ist konfiguriert — Sprint 2 kann sofort Features bauen, ohne Infrastruktur-Schulden."**

**Messbares Done-Kriterium:** Ein `curl`-Request mit gültigem httpOnly Cookie gibt `200 OK` zurück; GitHub Actions läuft durch ohne manuelles Eingreifen.

---

### 3. Was sollten wir bewusst NICHT in Sprint 1 nehmen?

| Task | Begründung |
|------|-----------|
| **BE-PDF-01 (8 SP)** — PDF-Parser | Risikoreichster Task im gesamten Backlog; ohne stabiles DB-Schema und Auth-Layer ist ein Proof-of-Concept sinnlos. Bewusst in Sprint 2 schieben. |
| **BE-CAT-02 (5 SP)** — Claude API Integration | Hängt von Lookup-Tabelle, Auth und DB ab — alle Vorbedingungen fehlen noch. |
| **FE-FC-01/02/03** — Fixkosten-Wizard Frontend | Angular-Skeleton reicht; vollständige UI-Komponenten brauchen den fertigen REST-Endpoint. |
| **DB-04 (2 SP)** — category_lookup Seed-Daten | Wird erst in Sprint 2/3 bei Kategorisierung gebraucht; jetzt schreiben = zu früh, Seed-Daten veralten. |
| **BE-STS-01/02/03** — Safe-to-Spend | Core-Feature, aber abhängig von Transaktionen + Fixkosten — mindestens Sprint 3. |
| **INFRA-05 (3 SP)** — Angular in JAR bundeln | Abhängig von stabilem Angular-Build und CI; erst sinnvoll wenn INFRA-03 grün läuft. → Sprint 2. |
| **INFRA-06 (1 SP)** — GitHub Actions CD | Setzt INFRA-05 voraus; CD ohne lauffähiges Bundle ist sinnlos. → Sprint 2. |

**Faustregel für Sprint 1:** Alles, was nicht Fundament ist (CI, DB, Auth), gehört nicht in diesen Sprint. Wenn die CI in Woche 1 nicht grün wird, leidet der Rest des Projekts dauerhaft.

---

### Kritischer Pfad Sprint 1

```
Tag 1–2:  INFRA-01 ⟂ INFRA-02  (parallel, je 1 Entwickler)
          + INFRA-04             (zusammen mit INFRA-01, reine Config)
Tag 2–3:  DB-01 → DB-02, DB-03  (nach INFRA-01)
Tag 3–5:  BE-AUTH-01             (nach DB-01)
Tag 4–5:  INFRA-03               (nach INFRA-01 + INFRA-02)
Tag 6–8:  BE-AUTH-02             (nach BE-AUTH-01)
Tag 8–10: Testen, DoD-Checks, Sprint Review vorbereiten
```


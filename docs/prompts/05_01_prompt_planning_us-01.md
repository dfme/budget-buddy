## Prompt

Wir wollen US-01 einplanen und auch noch in unser Backlog legen.
In Github findest du den aktuellen Stand unsere bereits definierte Issues (Sprint 1 ist bereits abgeschlossen).
Unser Tech-Stack: C2 Container Diagram aus @CLAUDE.md
Konventionen: In @CLAUDE.md
Unsere Definition of Done unter Antwort im @docs/prompts/04_01_prompt_uebung_dod_jason.md

Erstelle dafür Issues:
- Zerlege jede Story in Tasks (max 4h)
- Story Points pro Task (1,2,3,5,8)
- Bereich-Label (Backend/Frontend/DB/DevOps)
- Abhängigkeiten zwischen Tasks
- Sortiere nach: was muss zuerst gebaut werden?

Gib mir am Ende: Gesamt-Story-Points und grobe Reihenfolge als Markdown Output.


## Antwort

## US-01 — Konto erstellen und einloggen · Task-Zerlegung

> **Befund:** Das gesamte **Backend für US-01 ist bereits in Sprint 1 abgeschlossen** (DB-01 users-Tabelle, BE-AUTH-01 JWT-Filter, BE-AUTH-02 `/users/me`, BE-AUTH-03 register/login/logout mit bcrypt + httpOnly-Cookie + 409/401). Ebenso fertig: `credentialsInterceptor` (withCredentials global). **Offen ist ausschliesslich Frontend + E2E.**

### Tasks

| # | Task-ID | Titel | Bereich | SP | Aufwand | Abhängig von |
|---|---------|-------|---------|----|---------|--------------|
| 1 | **FE-AUTH-01** | AuthService (Signal-State + `/auth`-Calls) | Frontend | 3 | ~3–4h | — (BE fertig) |
| 2 | **FE-AUTH-02** | Login-Component (Reactive Form + Fehlerpfad „E-Mail oder Passwort falsch") | Frontend | 3 | ~3h | FE-AUTH-01 |
| 3 | **FE-AUTH-03** | Register-Component (Passwort ≥8, 409 → „E-Mail bereits vergeben") | Frontend | 3 | ~3h | FE-AUTH-01 |
| 4 | **FE-AUTH-04** | `authGuard` (CanActivate) + 401-Redirect für geschützte Routes | Frontend | 2 | ~2h | FE-AUTH-01 |
| 5 | **FE-AUTH-05** | Logout-Button + Nav-Anbindung (State-Reset → `/login`) | Frontend | 2 | ~2h | FE-AUTH-01 |
| 6 | **E2E-AUTH-01** | Playwright: Register → Login → Logout (Happy + Fehlerpfad) | DevOps/E2E | 3 | ~3h | FE-AUTH-02/03/04/05 |

### Abhängigkeitsgraph

```
FE-AUTH-01 (AuthService)
   ├── FE-AUTH-02 (Login)  ─┐
   ├── FE-AUTH-03 (Register)─┤
   ├── FE-AUTH-04 (Guard)   ─┼──► E2E-AUTH-01
   └── FE-AUTH-05 (Logout)  ─┘
```

FE-AUTH-01 ist der Flaschenhals — muss zuerst; danach können 02–05 **parallel** laufen (3 Devs). E2E ganz am Schluss, wenn der Flow durchgängig ist.

### Grobe Reihenfolge (Build-Order)

1. **FE-AUTH-01** — AuthService (blockiert alles andere) · *3 SP*
2. **FE-AUTH-02 + FE-AUTH-03 + FE-AUTH-04 + FE-AUTH-05** — parallel · *3+3+2+2 = 10 SP*
3. **E2E-AUTH-01** — End-to-End-Absicherung · *3 SP*

### Gesamt

| Kennzahl | Wert |
|----------|------|
| **Gesamt-Story-Points** | **16 SP** |
| Anzahl Tasks | 6 (5× Frontend, 1× E2E) |
| Kritischer Pfad | FE-AUTH-01 → (eine der 02–05) → E2E-AUTH-01 = **9 SP** |
| Backend-Aufwand | 0 (Sprint 1 abgeschlossen) |
| Einplanung | Backlog, Label `us-01`, kein Milestone |

> **Hinweis zur DoD:** US-01 ist MoSCoW *Should*; laut CLAUDE.md ist E2E strikt nur für Must-Have-Stories (US-03/04/05/06) vorgeschrieben. DoD-Kriterium 4 nennt aber einen automatisierten E2E-Happy-Path — daher als eigener Task (E2E-AUTH-01) enthalten. Falls E2E für US-01 wegfällt: **–3 SP → 13 SP total**.
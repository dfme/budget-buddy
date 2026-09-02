---
name: Task
about: Backlog-Task aus dem Sprint-Backlog
title: "[TASK-ID] Kurzbeschreibung"
labels: ""
assignees: ""
---

## Beschreibung

<!-- Was soll gebaut werden? Kontext und Ziel in 2–3 Sätzen. -->

## User Story

Gehört zu der User-Story: <!-- z.B. [US-03](../../docs/requirements/US-03-fixkosten-wizard.md) -->

## Acceptance Criteria

<!-- Was muss erfüllt sein, damit dieser Task als fertig gilt? -->

- [ ] 
- [ ] 
- [ ] 

## Abhängigkeiten

> Abhängigkeiten werden über die native **Relationships**-Funktion im rechten
> Seitenbereich des Issues gesetzt („Blocked by" / „Blocking"), **nicht** als
> Freitext hier. So bleiben sie bidirektional, für das Board sichtbar und als
> **eine** Quelle gepflegt — analog dazu, dass die Metadaten im Board liegen
> (vgl. INFRA-13). Kein zweites Datenhaltungs-Silo im Issue-Text.

- [ ] Falls dieser Task auf anderen wartet: **Blocked by** im Relationships-Panel gesetzt

## Metadaten

> Diese Werte werden **im [BudgetBuddy Sprint Board](https://github.com/users/dfme/projects/4)**
> gesetzt, nicht hier. Die Checkliste ist nur die Erinnerung daran — bitte direkt nach dem
> Anlegen des Issues im Board nachtragen.

- [ ] **Story Points** im Board gesetzt <!-- 1 / 2 / 3 / 5 / 8 -->
- [ ] **Area** im Board gesetzt <!-- Backend / Frontend / DB / DevOps -->
- [ ] **Sprint-Zuordnung im Team geklärt** — nicht selbst festlegen. Ergebnis ist entweder
      der Milestone des laufenden Sprints (`Sprint 3`, …) **oder** kein Milestone und
      Status `Backlog` im Board.

## Definition of Done

- [ ] Code ist reviewed (mind. 1 Approval im PR)
- [ ] `mvn package` und `ng build` laufen fehlerfrei durch
- [ ] Neue API-Endpoints sind in Swagger UI sichtbar (OpenAPI-Annotation vorhanden)
- [ ] Happy Path ist durch einen automatisierten Test abgedeckt — mit dem Framework, das zur
      Aufgabe passt: JUnit (Backend), Vitest/Angular TestBed (Frontend) oder Playwright (E2E).
      <!-- Die E2E-Abdeckung der Must-Have-Stories (US-03/04/05/06: je 1 Happy Path +
           1 Fehlerpfad, siehe docs/CONVENTIONS.md "Testing: Frameworks") ist davon unabhängig und
           wird in eigenen E2E-Tasks pro Story erfasst — nicht pro Issue. Dieser Punkt hier
           ist damit auch für eine Frontend-Story ohne Playwright erfüllbar. -->
- [ ] Alle Acceptance Criteria oben sind abhakbar erfüllt

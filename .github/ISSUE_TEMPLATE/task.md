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

<!-- Welche Tasks müssen vorher abgeschlossen sein? -->

- Depends on: <!-- z.B. #12, #15 -->
- Blocked by: <!-- leer lassen wenn keine Blocker -->

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
- [ ] Happy Path ist durch automatisierten Test abgedeckt (Playwright oder JUnit)
- [ ] Alle Acceptance Criteria oben sind abhakbar erfüllt

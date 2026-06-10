---
name: Task
about: Backlog-Task aus dem Sprint-Backlog
title: "[TASK-ID] Kurzbeschreibung"
labels: ""
assignees: ""
---

## Beschreibung

<!-- Was soll gebaut werden? Kontext und Ziel in 2–3 Sätzen. -->

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

| Feld | Wert |
|------|------|
| Story Points | <!-- 1 / 2 / 3 / 5 / 8 --> |
| Area | <!-- Backend / Frontend / DB / DevOps --> |
| Sprint | <!-- Sprint 1 / Sprint 2 / Backlog --> |
| Wave | <!-- Wave 0 / 1 / 2 / 3 --> |

## Definition of Done

- [ ] Code ist reviewed (mind. 1 Approval im PR)
- [ ] `mvn package` und `ng build` laufen fehlerfrei durch
- [ ] Neue API-Endpoints sind in Swagger UI sichtbar (OpenAPI-Annotation vorhanden)
- [ ] Happy Path ist durch automatisierten Test abgedeckt (Playwright oder JUnit)
- [ ] Alle Acceptance Criteria oben sind abhakbar erfüllt

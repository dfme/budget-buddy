# Plan: [INFRA-01] Spring Boot Skeleton anlegen

- **Issue:** #1
- **Task-ID:** INFRA-01
- **Branch:** `feature/INFRA-01-spring-boot-skeleton`
- **Area:** Backend/DevOps · Sprint 1 · Wave 0

## Ziel

Spring-Boot-Backend-Skeleton aufsetzen: `pom.xml` mit allen Dependencies,
`application.properties` (SQLite, kein hardcodierter Secret) und die
domänenbasierte Package-Struktur.

## Entscheide

- **Flyway:** Dependency aufnehmen, aber im Skeleton deaktiviert
  (`spring.flyway.enabled=false`). Aktivierung mit der ersten Migration in DB-01.
- **Security:** Minimale `SecurityConfig`, die Swagger UI, `/v3/api-docs` und
  Actuator-Health freigibt, Rest geschützt. Vollständige Auth-Logik in BE-AUTH-01.
- **Spring Boot 3.5.3** Parent auf Java 25 (`maven.compiler.release=25`).
  Flyway-Version wird vom Spring-Boot-BOM verwaltet (statt hart auf 10.x gepinnt),
  damit `mvn package` konfliktfrei durchläuft.

## Betroffene / neue Files (alle neu unter `backend/`)

- `backend/pom.xml`
- `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- `.gitignore` — Negationsregel für `maven-wrapper.jar`
- `backend/src/main/java/com/budgetbuddy/BudgetBuddyApplication.java`
- `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java`
- `backend/src/main/java/com/budgetbuddy/config/OpenApiConfig.java`
- `package-info.java` je Modul: `auth/`, `transaction/`, `categorization/`, `budget/`, `report/`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/db/migration/.gitkeep`
- `backend/src/test/java/com/budgetbuddy/BudgetBuddyApplicationTests.java`
- `backend/src/test/resources/application-test.properties` (`jdbc:sqlite::memory:`)

## Dependencies

- Starters: `web`, `security`, `data-jpa`, `validation`, `actuator`, `test`
- `org.xerial:sqlite-jdbc` 3.49.x
- `org.hibernate.orm:hibernate-community-dialects` (SQLiteDialect)
- `org.flywaydb:flyway-core` + `flyway-database-sqlite` (BOM-managed, deaktiviert)
- `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.x
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` 2.8.17
- `com.anthropic:anthropic-java` 2.31.0
- `org.apache.pdfbox:pdfbox` 3.0.x

## Implementierungsschritte

1. Branch erstellen (`main` → pull → feature branch)
2. `pom.xml` schreiben, Maven Wrapper generieren, `.gitignore` anpassen
3. Main-Klasse + Package-Struktur (`package-info.java` je Modul)
4. `SecurityConfig` + `OpenApiConfig`
5. `application.properties` (+ test-properties mit In-Memory-SQLite)
6. Context-Load-Test
7. `mvn package` lokal grün, Swagger-Pfade prüfen

## Test-Strategie

- **Integration:** `BudgetBuddyApplicationTests.contextLoads()` (`@SpringBootTest`,
  In-Memory-SQLite) — verifiziert, dass der gesamte Context inkl. Security/JPA/Springdoc
  startet. Automatisierter Happy-Path für die DoD.
- Kein E2E in diesem Skeleton-Task.

## Acceptance Criteria (aus Issue)

- [ ] `pom.xml` kompiliert fehlerfrei mit `mvn package`
- [ ] Package-Struktur auth/, transaction/, categorization/, budget/, report/ vorhanden
- [ ] application.properties enthält SQLite-DataSource, kein hardcodierter Secret

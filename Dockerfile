# syntax=docker/dockerfile:1

# --- Build-Stage: baut Frontend (Angular) + Backend (Spring Boot) in ein JAR ---
# Das Maven-Profil `prod` zieht den Angular-Build via frontend-maven-plugin ein
# und bündelt ihn als statische Ressourcen ins JAR (ADR-10, Single-Artifact).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Maven-Wrapper + POM zuerst kopieren (besseres Layer-Caching der Dependencies)
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml ./backend/

# Quellen kopieren: Backend-Sourcen und das gesamte Frontend (Input für ng build)
COPY backend/src ./backend/src
COPY frontend ./frontend

WORKDIR /app/backend
RUN ./mvnw -Pprod -DskipTests clean package

# --- Runtime-Stage: schlankes JRE-Image, nur das fertige JAR ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Prod-Profil aktivieren (kann via render.yaml überschrieben werden)
ENV SPRING_PROFILES_ACTIVE=prod

COPY --from=build /app/backend/target/budgetbuddy-*.jar app.jar

# Render gibt den Port über die Umgebungsvariable PORT vor; Spring Boot bindet
# darauf (siehe application-prod.properties: server.port=${PORT:8080}).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

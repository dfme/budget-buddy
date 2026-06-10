# spring-boot-with-angular-template

An **Angular 21** frontend served by a **Spring Boot 3 / Java 21** backend, built and
packaged into a **single executable JAR** by Maven.

## Layout

```
spring-boot-with-angular-template
├── pom.xml                         Maven build (bundles the frontend with -Pprod)
└── src
    ├── main
    │   ├── java/com/example/app
    │   │   ├── Application.java         Spring Boot entry point
    │   │   ├── api/GreetingController   Example REST endpoint (/api/greeting)
    │   │   └── config/SpaWebConfig      Serves the SPA + deep-link fallback
    │   ├── resources/application.yml
    │   └── frontend                 Angular 21 application
    │       ├── src/app              Components, services, routes
    │       └── proxy.conf.json      Dev proxy: /api -> localhost:8080
    └── test/java/...                Backend tests
```

## How the single JAR works

The frontend build lives in the `prod` Maven profile, so it only runs when that
profile is activated (`-Pprod`):

1. `exec-maven-plugin` runs `npm ci` + `npm run build` with the Node/npm installed
   on the build machine (locally or e.g. via `actions/setup-node` in CI).
2. Angular compiles into `src/main/frontend/dist`.
3. `maven-resources-plugin` copies that output into `target/classes/static`.
4. Spring Boot serves those static files from the classpath; `SpaWebConfig` falls back
   to `index.html` for client-side routes, while `/api/**` stays with the controllers.

Without `-Pprod`, Maven builds the backend only — ideal for fast local iteration.

## Build & run (production)

```bash
mvn clean package -Pprod
java -jar target/spring-boot-with-angular-template-0.0.1-SNAPSHOT.jar
```

Then open <http://localhost:8080>. The whole app (UI + API) runs on one port.

## Develop with live reload

Run the backend and the Angular dev server separately:

```bash
# Terminal 1 - backend API on :8080 (no frontend build, backend only)
mvn spring-boot:run

# Terminal 2 - Angular dev server on :4200 (proxies /api to :8080)
cd src/main/frontend
npm start
```

Open <http://localhost:4200> for hot-reloading. `/api` calls are proxied to the backend
via `proxy.conf.json`, so there are no CORS issues.

## Backend-only build

This is the default: without the `prod` profile, Maven never touches Node/npm.

```bash
mvn clean package              # backend-only JAR (no UI inside)
mvn spring-boot:run            # backend only, e.g. for API development
```

## Requirements

* Java 21+
* Maven 3.9+
* Node 22+, npm 11+ — needed for frontend work and for the production build (`-Pprod`),
  which expects `npm` on the `PATH` (in CI e.g. via `actions/setup-node`)

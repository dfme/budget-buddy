# Budgetbuddy

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.16.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

### Lokaler Dev-Betrieb (Frontend + Backend)

Die SPA ruft ihre API relativ auf (`/auth/*`, `/users/me`). Im Dev-Betrieb leitet ein Angular
Dev-Proxy (`proxy.conf.json`, in `angular.json` unter `serve` verdrahtet) diese Pfade an das
Spring-Boot-Backend auf `http://localhost:8080` weiter — so bleibt der Browser same-origin auf
`:4200`, das `Set-Cookie` (httpOnly JWT) kommt korrekt zurück, und es ist keine CORS-Konfiguration
im Backend nötig.

Beide Server parallel starten:

```bash
# Terminal 1 — Backend auf :8080
cd backend
./mvnw spring-boot:run

# Terminal 2 — Frontend Dev-Server auf :4200 (lädt den Proxy automatisch)
cd frontend
ng serve
```

App im Browser unter `http://localhost:4200/` öffnen. Ein `POST /auth/login` z. B. wird
transparent an `:8080` weitergereicht.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

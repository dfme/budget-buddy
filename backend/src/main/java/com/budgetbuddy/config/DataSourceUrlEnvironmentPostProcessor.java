package com.budgetbuddy.config;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Fail-fast-Prüfung von {@code spring.datasource.url} im prod-Profil (INFRA-25).
 *
 * <p>Ersetzt zwei Fehlerbilder, die beim ersten Neon-Deploy (DB-05, #89) beide erst spät und mit
 * irreführender Meldung sichtbar wurden: falsch benannte Variablen (App fällt still auf
 * {@code localhost} zurück) und ein vergessenes {@code jdbc:}-Präfix (Hikari-Stacktrace erst nach
 * vollständigem Docker-Build). Für {@code JWT_SECRET} löst {@code JwtProperties} das analoge
 * Problem über {@code @NotBlank}-Bean-Validation; das passt hier nicht, weil (a) drei der vier
 * Regeln keine simple Leer-Prüfung sind und (b) die Reihenfolge einer Bean-Validierung relativ zu
 * Hikari/pgjdbc nicht garantiert ist. Eine URL mit eingebetteten Zugangsdaten würde pgjdbc sonst
 * zweifach im Klartext loggen ({@code JDBC URL invalid port number: <passwort>@<host>} plus die
 * vollständige {@code jdbcUrl}) — die Prüfung muss also strukturell davor greifen, nicht nur meist.
 *
 * <p>Ein {@link EnvironmentPostProcessor} läuft während {@link SpringApplication#run}, bevor der
 * {@code ApplicationContext} existiert und damit bevor irgendeine Bean — insbesondere die
 * Hikari-{@code DataSource} — erzeugt wird. Das ist der einzige Punkt im Bootstrap, an dem sich
 * das garantieren lässt; eine {@code @Component} hätte dieselbe Garantie nicht, weil ihre
 * Instanziierungsreihenfolge relativ zu autokonfigurierten Beans nicht Teil des Spring-Vertrags
 * ist. Registriert über {@code META-INF/spring.factories}.
 *
 * <p>Nur aktiv, wenn {@code prod} im aktiven Profil steht — das Default-Profil (lokal gegen
 * Compose-Postgres) bleibt davon unberührt.
 *
 * <p>Zusätzlich per {@code budgetbuddy.datasource.url-failfast.enabled} abschaltbar (Default
 * {@code true}, siehe {@code application.properties}). Der Schalter existiert für
 * {@code ProdProfileSmokeTest}: der bootet das prod-Profil bewusst gegen einen
 * Testcontainers-Postgres auf {@code localhost}, registriert per {@code @DynamicPropertySource}
 * aber erst — Werte, die dieser {@link EnvironmentPostProcessor} zu seinem eigenen, viel früheren
 * Ausführungszeitpunkt noch nicht sieht. Ohne den Schalter würde dieser Check also einen legitimen
 * Test blockieren, den er gar nicht adressieren soll; pom.xml setzt ihn deshalb für alle Tests auf
 * {@code false} (Begründung dort, analog zu {@code budgetbuddy.anthropic.startup-healthcheck.enabled}).
 */
public class DataSourceUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROD_PROFILE = "prod";
    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String ENABLED_PROPERTY = "budgetbuddy.datasource.url-failfast.enabled";
    private static final String EXPECTED_FORMAT =
            "jdbc:postgresql://<host>.eu-central-1.aws.neon.tech/<db>?sslmode=require";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isProdProfileActive(environment) || !isEnabled(environment)) {
            return;
        }

        String url = environment.getProperty(URL_PROPERTY);

        if (!StringUtils.hasText(url)) {
            throw fail("SPRING_DATASOURCE_URL ist nicht gesetzt oder leer. Erwartetes Format: "
                    + EXPECTED_FORMAT);
        }
        if (!url.startsWith("jdbc:")) {
            throw fail("SPRING_DATASOURCE_URL beginnt nicht mit \"jdbc:\". Erwartetes Format: "
                    + EXPECTED_FORMAT);
        }
        if (containsEmbeddedCredentials(url)) {
            throw fail("SPRING_DATASOURCE_URL enthält Zugangsdaten vor dem Host (\"user:pass@host\"). "
                    + "Benutzer und Passwort gehören in SPRING_DATASOURCE_USERNAME und "
                    + "SPRING_DATASOURCE_PASSWORD, nicht in die URL.");
        }
        if (pointsToLocalhost(url)) {
            throw fail("SPRING_DATASOURCE_URL zeigt auf localhost/127.0.0.1. In Produktion muss sie "
                    + "auf die Neon-Instanz zeigen. Erwartetes Format: " + EXPECTED_FORMAT);
        }
    }

    private boolean isProdProfileActive(ConfigurableEnvironment environment) {
        return Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE);
    }

    private boolean isEnabled(ConfigurableEnvironment environment) {
        return environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE);
    }

    /**
     * Erkennt eingebettete Zugangsdaten wie {@code jdbc:postgresql://user:pass@host/db}: pgjdbc
     * liest alles vor dem ersten {@code :} nach {@code //} als Host und alles danach bis zum
     * {@code @} als Port — ein {@code @} in der Authority ist deshalb strukturell falsch, nicht
     * nur unerwünscht.
     */
    private boolean containsEmbeddedCredentials(String url) {
        String authority = extractAuthority(url);
        if (authority == null) {
            return url.contains("@");
        }
        return authority.contains("@");
    }

    /**
     * Exakter Host-Vergleich statt Substring-Test: {@code url.contains("localhost")} würde einen
     * legitimen Hostnamen wie {@code mylocalhost.example.com} fälschlich ablehnen.
     */
    private boolean pointsToLocalhost(String url) {
        String host = extractHost(url);
        return host != null && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
    }

    private String extractHost(String url) {
        String authority = extractAuthority(url);
        if (authority == null) {
            return null;
        }
        String hostPort = authority.contains("@") ? authority.substring(authority.indexOf('@') + 1) : authority;
        int portStart = hostPort.indexOf(':');
        return portStart == -1 ? hostPort : hostPort.substring(0, portStart);
    }

    private String extractAuthority(String url) {
        int authorityStart = url.indexOf("//");
        if (authorityStart == -1) {
            return null;
        }
        String afterScheme = url.substring(authorityStart + 2);
        int pathStart = afterScheme.indexOf('/');
        return pathStart == -1 ? afterScheme : afterScheme.substring(0, pathStart);
    }

    private IllegalStateException fail(String message) {
        return new IllegalStateException(message);
    }

    /**
     * Muss nach {@code ConfigDataEnvironmentPostProcessor} laufen, damit aktive Profile und
     * {@code application-prod.properties} bereits aufgelöst sind, wenn diese Klasse liest.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

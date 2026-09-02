package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Ohne {@code %X{}} im Log-Pattern bleibt der ganze MDC unsichtbar — der Filter setzt dann brav
 * Werte, die in keiner Zeile ankommen. Dieser Test hält deshalb das fest, was
 * {@code logging.pattern.level} tatsächlich rendert (INFRA-37).
 *
 * <p>Gelesen wird die reale {@code application.properties}, nicht ein Literal im Test: Ein Test,
 * der sein eigenes Pattern mitbringt, bliebe grün, während die Anwendung längst ohne MDC loggt.
 */
class LogPatternMdcTest {

    private static String levelPattern;

    @BeforeAll
    static void readConfiguredPattern() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = LogPatternMdcTest.class.getResourceAsStream(
                "/application.properties")) {
            assertThat(in).as("application.properties liegt im Classpath").isNotNull();
            properties.load(in);
        }
        levelPattern = properties.getProperty("logging.pattern.level");
    }

    @Test
    void patternRendersUserAndRequestId() {
        assertThat(levelPattern)
                .as("logging.pattern.level ist der Einhängepunkt für den MDC")
                .isNotNull();

        String rendered = render(levelPattern, Map.of(
                LogContext.USER_ID, "42",
                LogContext.REQUEST_ID, "a3f9c1d4"));

        assertThat(rendered)
                .as("beide IDs stehen in der Zeile, die in den Render-Logs landet")
                .contains("42")
                .contains("a3f9c1d4");
    }

    @Test
    void patternStaysReadableWithoutMdcValues() {
        // Zeilen vor der Filterkette (Startup, Actuator) tragen keinen MDC. Ohne Default-Wert
        // stünde dort ein leeres Klammerpaar — mit Default steht dort "none".
        String rendered = render(levelPattern, Map.of());

        assertThat(rendered)
                .as("Platzhalter fallen auf einen lesbaren Default zurück, nicht auf Leerstring")
                .contains("none");
    }

    /** Rendert ein Log-Event mit gesetztem MDC durch das konfigurierte Pattern. */
    private static String render(String pattern, Map<String, String> mdc) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern(pattern);
        layout.start();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerName(LogPatternMdcTest.class.getName());
        event.setLevel(Level.INFO);
        event.setMessage("beliebige Zeile");
        event.setMDCPropertyMap(mdc);

        try {
            return layout.doLayout((ILoggingEvent) event);
        } finally {
            layout.stop();
        }
    }
}

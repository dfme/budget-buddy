package com.budgetbuddy.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread-Pool für den asynchronen Teil des PDF-Imports (BE-PDF-09, ADR-14).
 *
 * <p>Seit ADR-14 parst der Upload-Request synchron und übergibt die Kategorisierung an einen
 * Hintergrund-Job. Dieser Pool führt ihn aus.
 *
 * <p><strong>Bewusst klein und begrenzt:</strong> Jeder laufende Job hält die geparsten
 * Transaktionen eines Auszugs im Speicher und hängt die meiste Zeit an einem HTTP-Call zu Claude.
 * Ein unbegrenzter Pool (Springs Default ist {@code SimpleAsyncTaskExecutor}: ein neuer Thread pro
 * Aufruf) würde bei parallelen Uploads sowohl Speicher als auch das Anthropic-Rate-Limit
 * ungebremst belasten. Zwei Threads reichen für den MVP-Betrieb auf einer Render-Starter-Instanz;
 * die Queue fängt Spitzen ab, ohne einen Request warten zu lassen.
 *
 * <p>Läuft auch die Queue über, wirft der Executor eine {@code TaskRejectedException} — der
 * Aufrufer markiert den Job dann als {@code FAILED} (siehe {@code PdfImportService}), statt den
 * Upload-Request zu blockieren.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Bean-Name, auf den {@code @Async} im Import-Pfad verweist. */
    public static final String IMPORT_EXECUTOR = "importExecutor";

    @Bean(IMPORT_EXECUTOR)
    public Executor importExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pdf-import-");
        // Ohne den Decorator liefe der Import ohne User-ID im Log — der Pool-Thread kennt den
        // MDC des Upload-Requests nicht (INFRA-37).
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Ein laufender Import soll beim Shutdown noch fertig werden — sonst stünde ein Job auf
        // RUNNING und das Frontend pollte ins Leere.
        //
        // Diese Wartezeit ist aber keine Garantie, und bis BE-PDF-11 (#197) las sich der Kommentar
        // hier so, als wäre sie eine: Renders Grace-Period vor dem SIGKILL ist kürzer als diese
        // 60 Sekunden, ein Import kann also mitten im Lauf sterben. Aufgefangen wird das nicht
        // hier, sondern vom StaleImportJobCleaner, der solche Zeilen beim nächsten Start und
        // danach periodisch auf FAILED setzt.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}

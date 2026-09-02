package com.budgetbuddy.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * Trägt den Logging-Kontext des aufrufenden Threads in den Pool-Thread hinüber (INFRA-37).
 *
 * <p>Der MDC ist Thread-lokal. Der {@code @Async}-Importlauf startet auf einem Thread des
 * {@code importExecutor} und wüsste ohne diesen Decorator nichts von der User-ID des Requests,
 * der ihn ausgelöst hat — ausgerechnet dort, wo der lange, fehleranfällige Teil des Imports
 * läuft (ADR-14).
 *
 * <p>Die Map wird beim <em>Einreihen</em> des Tasks gelesen, nicht bei seiner Ausführung: Zu
 * diesem Zeitpunkt läuft noch der Request-Thread und trägt den Kontext. Nach dem Lauf wird der
 * Pool-Thread wieder geleert, sonst liefe der nächste Import unter der User-ID des vorherigen.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            if (callerContext != null) {
                MDC.setContextMap(callerContext);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}

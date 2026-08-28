package com.budgetbuddy.support;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@link ListAppender}, der ausschliesslich Log-Events des Threads aufnimmt, der ihn angelegt hat
 * (BE-CAT-07).
 *
 * <p>Ein Logback-Logger ist prozessweit geteilt. Ein Test, der einen Appender anhängt, sieht damit
 * <em>alles</em>, was irgendein Thread der JVM auf diesen Logger schreibt — auch Zeilen, die eine
 * längst beendete Testklasse asynchron nachliefert. Genau das machte
 * {@code AnthropicStartupHealthCheckTest} ordnungsabhängig (#162): ein
 * {@code CompletableFuture.runAsync}-Thread aus einem fremden Spring-Kontext überlebte seine
 * Testklasse und landete Sekunden später im WARN-Puffer eines anderen Tests.
 *
 * <p>Die naheliegende Gegenmassnahme — den Appender pro Testfall neu anlegen — greift nicht: Er
 * <em>war</em> pro Testfall neu. Ein Fremd-Thread trifft denjenigen Appender, der im Moment des
 * Loggens angehängt ist, unabhängig von dessen Alter. Die Eingrenzung muss deshalb am Ursprung
 * des Events ansetzen, nicht an dessen Lebensdauer.
 *
 * <p>Der Filter wirkt <strong>in beide Richtungen</strong>. Er verhindert nicht nur das
 * fälschliche Rot eines {@code noneMatch}, sondern auch das fälschliche Grün eines
 * {@code anyMatch}, das eine fremde Zeile erfüllt hätte — der teurere der beiden Fehler, weil er
 * unbemerkt bleibt.
 *
 * <p><strong>Grenze:</strong> Ein Test, der bewusst auf Logs eines eigenen Hintergrund-Threads
 * prüfen will, kann diesen Appender nicht verwenden. Das ist beabsichtigt: Wer das braucht, soll
 * die Thread-Zugehörigkeit explizit modellieren, statt sie versehentlich mitzunehmen.
 */
public final class ThreadScopedLogAppender extends ListAppender<ILoggingEvent> {

    /**
     * Name des anlegenden Threads. Logback erfasst {@link ILoggingEvent#getThreadName()} beim
     * Erzeugen des Events auf dem loggenden Thread — der Vergleich trennt Ursprünge also korrekt,
     * auch wenn der Appender selbst von mehreren Threads berührt wird.
     */
    private final String owningThread = Thread.currentThread().getName();

    @Override
    protected void append(ILoggingEvent event) {
        if (owningThread.equals(event.getThreadName())) {
            super.append(event);
        }
    }
}

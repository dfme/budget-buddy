package com.budgetbuddy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aktiviert Springs {@code @Scheduled}-Verarbeitung (BE-PDF-11).
 *
 * <p>Ohne diese Annotation wird {@code @Scheduled} still ignoriert — die Methode existiert, wird
 * aber nie aufgerufen, und nichts im Log weist darauf hin. Vor BE-PDF-11 gab es im Backend keinen
 * einzigen periodischen Lauf; diese Konfiguration ist der Einstiegspunkt für alle künftigen.
 *
 * <p><strong>Bewusst eine eigene Klasse und nicht {@link AsyncConfig}.</strong> Die beiden
 * Mechanismen sehen verwandt aus, sind es aber nicht: {@code @EnableAsync} dort gehört zum
 * Import-Pool, dessen Grösse und Shutdown-Verhalten aus den Anforderungen des PDF-Imports folgen.
 * {@code @EnableScheduling} ist dagegen anwendungsweit. Sie zusammenzulegen hiesse, den Namen
 * {@code AsyncConfig} für etwas mitzuverwenden, das er nicht benennt.
 *
 * <p>Der Scheduler läuft mit Springs Default-Pool von <strong>einem</strong> Thread. Das genügt,
 * solange es bei wenigen kurzen Läufen bleibt; kommt je eine länger laufende Aufgabe dazu, blockiert
 * sie die übrigen und der Pool gehört hier vergrössert.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

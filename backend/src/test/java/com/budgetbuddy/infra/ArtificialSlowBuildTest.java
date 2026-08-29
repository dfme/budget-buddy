package com.budgetbuddy.infra;

import org.junit.jupiter.api.Test;

/**
 * WEGWERF-TEST für INFRA-35 (#224): verlangsamt {@code ./mvnw package} künstlich, um den
 * automatischen review-pr-Lauf zu zwingen, eine spürbar lange Verifikation zu treffen. Wird
 * niemals gemerged — dieser Branch dient ausschliesslich der manuellen Verifikation der neuen
 * SKILL.md-Anweisung in {@code .claude/skills/review-pr/SKILL.md}.
 */
class ArtificialSlowBuildTest {

    @Test
    void artificiallySlowsDownTheBuild() throws InterruptedException {
        Thread.sleep(150_000);
    }
}

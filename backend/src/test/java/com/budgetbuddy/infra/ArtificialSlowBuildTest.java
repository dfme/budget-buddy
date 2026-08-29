package com.budgetbuddy.infra;

import org.junit.jupiter.api.Test;

/**
 * WEGWERF-TEST für INFRA-35 (#224): verlangsamt {@code ./mvnw package} künstlich, um den
 * automatischen review-pr-Lauf zu zwingen, eine spürbar lange Verifikation zu treffen. Wird
 * niemals gemerged — dieser Branch dient ausschliesslich der manuellen Verifikation des neuen
 * Guards in {@code .github/workflows/claude-pr-review.yml}.
 */
class ArtificialSlowBuildTest {

    @Test
    void artificiallySlowsDownTheBuild() throws InterruptedException {
        Thread.sleep(150_000);
    }
}

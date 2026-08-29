package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Reine Unit-Tests gegen {@link DataSourceUrlEnvironmentPostProcessor} direkt, ohne echten
 * Spring-Context — analog zu {@code AnthropicStartupHealthCheckTest}: die Prüfung arbeitet rein
 * auf dem Property-String, ein Boot eines vollen Kontexts wäre hier nur Ballast.
 */
class DataSourceUrlEnvironmentPostProcessorTest {

    private final DataSourceUrlEnvironmentPostProcessor postProcessor =
            new DataSourceUrlEnvironmentPostProcessor();

    @Test
    void blankUrlInProdProfileFailsFast() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("SPRING_DATASOURCE_URL")
                .withMessageContaining("jdbc:postgresql://");
    }

    @Test
    void localhostUrlInProdProfileFailsFast() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/budgetbuddy");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("localhost");
    }

    @Test
    void missingJdbcPrefixInProdProfileFailsFast() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "postgresql://ep-abc.eu-central-1.aws.neon.tech/budgetbuddy");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("jdbc:");
    }

    @Test
    void embeddedCredentialsInProdProfileFailsFastWithoutLeakingPassword() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://neonuser:s3cr3t-passw0rd@ep-abc.eu-central-1.aws.neon.tech/budgetbuddy");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("SPRING_DATASOURCE_URL")
                .withMessageNotContaining("s3cr3t-passw0rd")
                .withMessageNotContaining("neonuser");
    }

    @Test
    void validNeonUrlInProdProfilePasses() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(
                "spring.datasource.url",
                "jdbc:postgresql://ep-abc.eu-central-1.aws.neon.tech/budgetbuddy?sslmode=require");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void hostnameContainingLocalhostAsSubstringIsNotFalselyRejected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://mylocalhost.example.com/budgetbuddy?sslmode=require");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidUrlOutsideProdProfileIsIgnored() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("default");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/budgetbuddy");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidUrlInProdProfileIsIgnoredWhenFailFastDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/budgetbuddy");
        environment.setProperty("budgetbuddy.datasource.url-failfast.enabled", "false");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }
}

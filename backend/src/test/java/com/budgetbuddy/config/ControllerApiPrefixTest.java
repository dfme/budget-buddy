package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Härtet die Invariante aus INFRA-17 (#126, AC #5): jeder Projekt-Controller lebt unter
 * {@code /api/**}, sonst ist er über {@code GET /** permitAll} in {@link SecurityConfig}
 * unbeabsichtigt öffentlich. Die vorherige Doppelpflege (SecurityConfig und
 * SpaForwardController kannten dieselbe Routen-Liste unabhängig) ist beseitigt, aber sie wurde
 * durch eine neue, ungeschützte Invariante ersetzt — dieser Test schliesst genau diese Lücke
 * (Review zu PR #187).
 *
 * <p>Reproduziert den Fund aus dem Review: der Ad-hoc-Test-Endpoint {@code /test/me} in
 * {@code JwtCookieAuthenticationFilterTest} lag ausserhalb {@code /api/**} und wurde durch
 * {@code GET /** permitAll} ungewollt öffentlich — sichtbar wurde das nur, weil der Endpoint
 * zufällig einen {@code Authentication}-Parameter brauchte und mit NPE fiel. Ein Endpoint, der
 * die User-ID stattdessen aus einem Service liest, wäre ohne rotes Signal durchgerutscht.
 *
 * <p>Bewusst auf {@code com.budgetbuddy}-Controller beschränkt statt auf jeden registrierten
 * Handler: Springdoc registriert seinen {@code /v3/api-docs}-Controller ebenfalls über
 * {@link RequestMappingHandlerMapping}, aber ausserhalb von {@code /api/**} — das ist gewollt
 * (siehe {@link SecurityConfig#PUBLIC_PATHS}), keine Verletzung dieser Invariante. Eine
 * Prüfung über alle Handler hätte hier falsch-positiv angeschlagen.
 */
@SpringBootTest
@ActiveProfiles("test")
class ControllerApiPrefixTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "controller_api_prefix");
    }

    // Zwei Beans dieses Typs: die echte MVC-Handler-Mapping ("requestMappingHandlerMapping")
    // und Actuators "controllerEndpointHandlerMapping" für @ControllerEndpoint-Beans (hier
    // ungenutzt) — ohne Qualifier wirft der Kontext eine NoUniqueBeanDefinitionException.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyProjectControllerMappingLivesUnderApi() {
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Class<?> beanType = method.getBeanType();
            if (!beanType.getPackageName().startsWith("com.budgetbuddy")) {
                return;
            }
            if (beanType.equals(SpaForwardController.class)) {
                return;
            }
            info.getPathPatternsCondition().getPatternValues().forEach(pattern ->
                assertThat(pattern)
                    .as("%s liegt ausserhalb /api/** und waere durch \"GET /** permitAll\" "
                        + "unbeabsichtigt oeffentlich", method.getShortLogMessage())
                    .startsWith("/api"));
        });
    }
}

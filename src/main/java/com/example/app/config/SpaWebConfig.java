package com.example.app.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the compiled Angular single-page application from the classpath.
 *
 * <p>Any request that does not match an existing static file and is not an API
 * call falls back to {@code index.html}, so that Angular's client-side router
 * can handle deep links and page reloads (e.g. navigating directly to /users/42).
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Let real API 404s surface instead of returning the SPA shell.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return new org.springframework.core.io.ClassPathResource("/static/index.html");
                    }
                });
    }
}

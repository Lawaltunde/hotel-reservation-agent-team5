package com.reservationagent.hotelreservationagent.presentation.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Next.js static export (packaged by the frontend module under classpath:/static/).
 * Next writes each page as {@code <route>.html}, so {@code /search} resolves to {@code search.html}.
 * Controllers ({@code /api/**}) and actuator are matched before this handler.
 */
@Configuration
public class FrontendResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new NextExportResourceResolver());
    }

    static class NextExportResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource exact = super.getResource(resourcePath, location);
            if (exact != null) {
                return exact;
            }
            if (resourcePath.isEmpty() || resourcePath.contains(".")) {
                return null;
            }
            String page = resourcePath.endsWith("/") ? resourcePath + "index.html" : resourcePath + ".html";
            return super.getResource(page, location);
        }
    }
}

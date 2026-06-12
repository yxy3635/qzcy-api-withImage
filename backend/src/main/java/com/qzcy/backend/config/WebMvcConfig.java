package com.qzcy.backend.config;

import com.qzcy.backend.util.UploadPathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Value("${app.upload.image-path:userImage/}")
    private String imagePath;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://image.qzcy3.top,https://image.qzcy3.top,http://*.qzcy3.top,https://*.qzcy3.top}")
    private String[] allowedOriginPatterns;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = UploadPathUtil.resolveImageRoot(imagePath, WebMvcConfig.class).toUri().toString();
        log.info("Image static resource mapping: /api/images/** -> {}", location);
        registry.addResourceHandler("/api/images/**").addResourceLocations(location);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.AUTHORIZATION)
                .allowCredentials(true)
                .maxAge(3600);
    }
}

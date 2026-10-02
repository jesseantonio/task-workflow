package com.taskworkflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Libera o front-end (Vite dev server) a chamar a API de uma origem diferente. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/tasks/**")
                // porta varia conforme o Vite encontra 5173 livre ou não (5174, 5175...).
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

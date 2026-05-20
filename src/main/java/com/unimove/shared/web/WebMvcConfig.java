package com.unimove.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LastSeenInterceptor lastSeenInterceptor;

    public WebMvcConfig(LastSeenInterceptor lastSeenInterceptor) {
        this.lastSeenInterceptor = lastSeenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(lastSeenInterceptor)
                .excludePathPatterns("/auth/**", "/actuator/**");
    }
}

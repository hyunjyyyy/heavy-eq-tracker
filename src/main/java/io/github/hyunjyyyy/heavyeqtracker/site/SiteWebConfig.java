package io.github.hyunjyyyy.heavyeqtracker.site;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SiteWebConfig implements WebMvcConfigurer {

    private final SiteContextInterceptor interceptor;

    public SiteWebConfig(SiteContextInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/sites/*/**", "/api/v1/hq/**");
    }
}

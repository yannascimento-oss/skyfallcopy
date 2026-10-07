package org.empresajr.chatjr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ActiveAccountInterceptor activeAccountInterceptor;

    public WebConfig(ActiveAccountInterceptor activeAccountInterceptor) {
        this.activeAccountInterceptor = activeAccountInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activeAccountInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/setup/**");
    }
}

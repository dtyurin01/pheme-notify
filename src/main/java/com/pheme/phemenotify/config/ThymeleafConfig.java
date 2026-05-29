package com.pheme.phemenotify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.nio.charset.StandardCharsets;

@Configuration
public class ThymeleafConfig {

    @Bean
    public ITemplateResolver textTemplateResolver() {
        return createResolver(".txt", TemplateMode.TEXT, 1);
    }

    @Bean
    public ITemplateResolver htmlTemplateResolver() {
        return createResolver(".html", TemplateMode.HTML, 2);
    }

    private ClassLoaderTemplateResolver createResolver(String suffix, TemplateMode mode, int order) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setOrder(order);
        resolver.setCheckExistence(true);
        return resolver;
    }
}

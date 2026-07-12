package com.vinoigitare.web;

import java.time.Duration;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * The real language switcher (2026-07-12): a visitor's chosen locale is
 * read from/written to a cookie (not the HTTP session -- a returning
 * visitor a week later should still see their last choice without
 * needing to pick it again, the same "persists client-side, no server
 * state" spirit as the dark/light theme toggle's own localStorage use),
 * changed by visiting any URL with a {@code ?lang=} query parameter
 * (English {@code en}, Croatian {@code hr}, or Serbian {@code sr} -- see
 * {@code messages.properties}/{@code messages_hr.properties}/{@code
 * messages_sr.properties}).
 *
 * <p>Replaces the old {@code spring.mvc.locale}/{@code locale-resolver:
 * fixed} configuration in {@code application.yml}, which hard-locked
 * English with no way to override it at all -- see that file's own
 * comment on why English still stays the default for a first-time
 * visitor (not auto-detected from the browser), just no longer the
 * *only* option.
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    private static final String COOKIE_NAME = "vinoigitare.locale";
    private static final String LOCALE_CHANGE_PARAM = "lang";

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(COOKIE_NAME);
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(LOCALE_CHANGE_PARAM);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}

package com.vinoigitare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Admin-auth plan (~/knowledge/projects/vinoigitare/admin-auth-plan.md): a
 * single admin credential (see {@code application.yml}'s {@code
 * spring.security.user.*}, sourced from {@code VINOIGITARE_ADMIN_USER}/
 * {@code VINOIGITARE_ADMIN_PASSWORD}) gating only {@code /admin/**} --
 * every other route in this app is a public, read-only song-browsing page
 * and must stay reachable without authentication. Resolves the TODO that's
 * been sitting on {@code AdminController}'s class Javadoc since Phase 1.
 *
 * <p>Deliberately no visitor accounts, roles, or database-backed user
 * store -- see the plan doc for why. Phase 5 of the migration plan
 * (accounts/community) was skipped entirely and stays skipped; this is
 * the one narrow piece of it actually needed, not a reversal of that
 * decision.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/artists/**", "/akordi/**", "/search",
                                "/chord-diagrams", "/sitemap.xml",
                                "/css/**", "/js/**", "/webjars/**",
                                "/actuator/health", "/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll());
        return http.build();
    }

    /**
     * Registering a custom {@code loginPage(...)} above means Spring
     * Security stops auto-generating one -- it now expects the app to
     * serve {@code GET /login} itself. A plain view-controller mapping is
     * enough: {@code templates/login.html} needs no model data, just the
     * {@code ?error}/{@code ?logout} query parameters Spring Security
     * appends itself (read directly as request params in the template, see
     * its comment). Kept in this class, next to the {@code loginPage(...)}
     * call it exists for, rather than a separate {@code WebMvcConfigurer}
     * file elsewhere -- so the two can't drift out of sync.
     */
    @Bean
    public WebMvcConfigurer loginViewConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/login").setViewName("login");
            }
        };
    }
}

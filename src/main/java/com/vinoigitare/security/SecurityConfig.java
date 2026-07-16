package com.vinoigitare.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
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
                                "/chord-diagrams", "/about", "/offline", "/sitemap.xml", "/robots.txt",
                                "/css/**", "/js/**", "/webjars/**", "/icons/**", "/downloads/**", "/images/**",
                                "/manifest.json", "/sw.js",
                                // Visitor feedback form -- this app's first genuinely public,
                                // unauthenticated POST route (every other public route is GET-only;
                                // the other POST routes, admin/login, require auth). CSRF protection
                                // still applies regardless of permitAll -- that's authorization, a
                                // separate concern.
                                "/feedback",
                                "/actuator/health", "/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                // The feedback form (about.html) is this app's first CSRF-protected
                // form on a route that isn't behind login -- until now CSRF tokens
                // only ever needed the session that formLogin already creates.
                // Session-backed CsrfTokenRepository (the default) would mean every
                // visitor to /about gets an HttpSession/JSESSIONID just for viewing a
                // page, which both contradicts about.html's "no tracking cookies"
                // privacy claim and doesn't even work reliably: /about is long enough
                // that its response can commit before rendering reaches the form
                // further down the page, and by then it's too late to open a session
                // -- IllegalStateException: "Cannot create a session after the
                // response has been committed". A cookie-backed repository sidesteps
                // both problems since it never touches HttpSession.
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                // Even cookie-based, the token is deferred -- not resolved (and its
                // Set-Cookie written) until something reads it, which for Thymeleaf's
                // automatic hidden field is whenever the template engine reaches the
                // <form> tag. Forcing that resolution here, right after CsrfFilter and
                // before any response body is written, guarantees the cookie is set
                // before the same commit-order problem described above can bite. See
                // https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#deferred-csrf-token
                .addFilterAfter((request, response, chain) -> {
                    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                    if (csrfToken != null) {
                        csrfToken.getToken();
                    }
                    chain.doFilter(request, response);
                }, CsrfFilter.class);
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

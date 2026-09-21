package com.tanidikvar.api.config;

import com.tanidikvar.api.common.error.ApiErrors;
import java.util.List;
import com.tanidikvar.api.auth.security.*;
import com.tanidikvar.api.auth.service.AuthenticationService;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper, @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource,
            @Value("${app.secure-cookies}") boolean secure, AuthenticationService authentication, AuthCookies cookies, AuthRateLimiter limiter,
            ClientAddressResolver addresses) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax").path("/"));
        return http.cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new AuthRateLimitFilter(limiter, addresses, mapper), CsrfFilter.class)
                .addFilterAfter(new CookieAuthenticationFilter(authentication, cookies, mapper), AuthRateLimitFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/resend-verification", "/api/auth/verify-email", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/universities", "/api/universities/*", "/api/universities/*/departments", "/api/universities/*/departments/*", "/api/universities/*/catalog-statistics", "/api/departments", "/api/programs", "/api/catalog-programs", "/api/catalog-programs/*", "/api/statistics/overview", "/api/tags").permitAll()
                        .requestMatchers("/api/manager/**").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/questions").hasAnyRole("USER", "YKS_ADAYI", "UNIVERSITE_OGRENCISI", "MEZUN", "TANIDIK")
                        .requestMatchers(HttpMethod.GET, "/api/profiles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/gamification/profiles/*", "/api/gamification/profiles/*/annual-report", "/api/gamification/leaderboard").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/evaluations", "/api/evaluations/summary").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/evaluations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/polls").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/context-metrics", "/api/experiences", "/api/career-outcomes", "/api/experience-sentiments", "/api/gamification/profiles/*/achievements").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/question-templates", "/api/answers/*/comments").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/context-metrics", "/api/career-outcomes", "/api/questions/*/best-answer", "/api/me/notification-preferences", "/api/me/gamification/showcase").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/experiences", "/api/answers/*/comments", "/api/me/education-verification/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me/education-verification", "/api/me/notification-preferences").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/polls").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/polls/*/vote").authenticated()
                        .requestMatchers("/api/me/follows", "/api/me/saved", "/api/me/notifications", "/api/me/notifications/*/read").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/questions/*/answers", "/api/questions/*/admin-answers", "/api/questions/*/reports", "/api/answers/*/reports", "/api/answer-comments/*/reports", "/api/me/admin-applications").hasAnyRole("USER", "YKS_ADAYI", "UNIVERSITE_OGRENCISI", "MEZUN", "TANIDIK")
                        .requestMatchers(HttpMethod.PUT, "/api/questions/*/like", "/api/questions/*/assignment", "/api/answers/*", "/api/answers/*/comments/*", "/api/answers/*/like", "/api/answers/*/status", "/api/admin-answers/*", "/api/admin-answers/*/status", "/api/questions/*").hasAnyRole("USER", "YKS_ADAYI", "UNIVERSITE_OGRENCISI", "MEZUN", "TANIDIK")
                        .requestMatchers(HttpMethod.POST, "/api/questions/*/archive", "/api/questions/*/restore").hasAnyRole("USER", "YKS_ADAYI", "UNIVERSITE_OGRENCISI", "MEZUN", "TANIDIK")
                        .requestMatchers("/api/me/admin-applications", "/api/me/tanidik-applications", "/api/files/*/download").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/tags").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/statistics").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/questions/*/views").permitAll()
                        .requestMatchers("/api/questions/*/like").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/answers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/answers/*/like").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/admin-answers", "/api/admins/*", "/api/admins/*/answers").permitAll()
                        .requestMatchers("/api/questions/*/my-admin-answer", "/api/questions/*/assignment", "/api/me/admin-quota", "/api/me/admin-answers", "/api/me/assignments").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/my-answer").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/popular", "/api/admins", "/api/tanidiklar", "/api/tanidiklar/*", "/api/tanidiklar/*/answers", "/api/questions", "/api/questions/*").permitAll()
                        .requestMatchers("/api/me/profile", "/api/me/questions", "/api/me/answers").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(mapper.writeValueAsString(ApiErrors.create(request, 401, "AUTHENTICATION_REQUIRED", "Oturum açman gerekiyor.")));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(mapper.writeValueAsString(ApiErrors.create(request, 403, "ACCESS_DENIED", "Bu işlem için erişim iznin yok.")));
                        }))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origin}") String origin) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("X-Request-ID", "Retry-After"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

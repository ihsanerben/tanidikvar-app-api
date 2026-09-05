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
            @Value("${app.secure-cookies}") boolean secure, AuthenticationService authentication, AuthCookies cookies, AuthRateLimiter limiter) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax").path("/"));
        return http.cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new AuthRateLimitFilter(limiter, mapper), CsrfFilter.class)
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
                        .requestMatchers(HttpMethod.GET, "/api/universities", "/api/universities/*/departments", "/api/departments", "/api/tags").permitAll()
                        .requestMatchers("/api/manager/**").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/avatars/*").permitAll()
                        .requestMatchers("/api/me/admin-applications", "/api/me/avatar", "/api/me/avatar/remove", "/api/files/*/download").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/tags").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/answers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/admin-answers", "/api/admins/*", "/api/admins/*/answers").permitAll()
                        .requestMatchers("/api/questions/*/my-admin-answer", "/api/questions/*/assignment", "/api/me/admin-quota", "/api/me/admin-answers", "/api/me/assignments").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/questions/*/admin-answers").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/admin-answers/*", "/api/admin-answers/*/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/questions/*/my-answer").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/questions/*/answers").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/answers/*", "/api/answers/*/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/questions", "/api/questions/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/questions", "/api/questions/*/archive").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/questions/*").authenticated()
                        .requestMatchers("/api/me/profile", "/api/me/questions").authenticated()
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

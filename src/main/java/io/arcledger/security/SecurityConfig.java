package io.arcledger.security;

import io.arcledger.api.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.*;

import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder(@Value("${arcledger.security.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errors, SecurityAuditService audit,
                                            SecurityContextRepository securityContextRepository,
                                            CorsConfigurationSource corsConfigurationSource,
                                            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .requestCache(cache -> cache.disable())
            .headers(headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                    "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; " +
                    "form-action 'self'; frame-ancestors 'none'"))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin")))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/assets/**", "/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    audit.record("AUTHENTICATION_REQUIRED", "REJECTED", null, request);
                    errors.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    boolean csrfFailure = exception instanceof CsrfException;
                    audit.record(csrfFailure ? "CSRF_REJECTED" : "ACCESS_DENIED", "REJECTED", null, request);
                    errors.write(request, response, HttpStatus.FORBIDDEN,
                        csrfFailure ? "INVALID_CSRF_TOKEN" : "FORBIDDEN",
                        csrfFailure ? "Request security token is missing or expired." : "You do not have permission to perform this action.");
                }))
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .addLogoutHandler((request, response, authentication) ->
                    audit.record("LOGOUT", "SUCCEEDED", audit.actorId(authentication), request))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("ARCLEDGER_SESSION", "JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${arcledger.security.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::strip).filter(origin -> !origin.isBlank()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("X-Request-ID", "Retry-After", "RateLimit-Limit",
            "RateLimit-Remaining", "RateLimit-Reset", "Idempotency-Replayed"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}

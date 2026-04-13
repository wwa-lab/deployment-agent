package com.wwa.agenthub.config;

import com.wwa.agenthub.web.security.GuestReadOnlyFilter;
import com.wwa.agenthub.web.security.HeaderAuthFilter;
import com.wwa.agenthub.web.security.SessionAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * <p>Authentication chain: SessionAuthFilter → HeaderAuthFilter → UsernamePasswordAuth.
 * Session-based auth (from login) takes priority; header-based auth (legacy/test)
 * only fires if not already authenticated.
 */
@Configuration
@EnableWebSecurity
@Profile("!local")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    SessionAuthFilter sessionAuthFilter,
                                                    HeaderAuthFilter headerAuthFilter,
                                                    GuestReadOnlyFilter guestReadOnlyFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/platform/auth/login").permitAll()
                    .requestMatchers("/api/platform/auth/guest").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) ->
                            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED)))
            .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(headerAuthFilter, SessionAuthFilter.class)
            .addFilterAfter(guestReadOnlyFilter, HeaderAuthFilter.class);

        return http.build();
    }
}

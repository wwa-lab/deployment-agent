package com.wwa.agenthub.config;

import com.wwa.agenthub.web.security.GuestReadOnlyFilter;
import com.wwa.agenthub.web.security.HeaderAuthFilter;
import com.wwa.agenthub.web.security.SessionAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http,
                                                        SessionAuthFilter sessionAuthFilter,
                                                        HeaderAuthFilter headerAuthFilter,
                                                        GuestReadOnlyFilter guestReadOnlyFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(headerAuthFilter, SessionAuthFilter.class)
                .addFilterAfter(guestReadOnlyFilter, HeaderAuthFilter.class);

        return http.build();
    }
}

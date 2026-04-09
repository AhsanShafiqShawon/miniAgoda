package com.miniagoda.common.config;

import com.miniagoda.common.filter.JwtAuthenticationFilter;
import com.miniagoda.common.util.SecurityErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorWriter securityErrorWriter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityErrorWriter securityErrorWriter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("POST", "/api/auth/**").permitAll()
                .requestMatchers("GET",  "/api/hotels/**").permitAll()
                .requestMatchers("GET",  "/api/search/**").permitAll()
                .requestMatchers("/api/host/**").hasRole("HOST")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeError(response, request, 401, "Unauthorized", "Authentication is required to access this resource.")
                )
                .accessDeniedHandler((request, response, e) ->
                    writeError(response, request, 403, "Forbidden", "You do not have permission to perform this action.")
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                        int status, String error, String message) throws IOException {
    securityErrorWriter.write(response, request, status, error, message);
}

}
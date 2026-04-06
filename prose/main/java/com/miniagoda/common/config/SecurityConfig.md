package com.shawon.miniagoda.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // TODO: Inject JwtAuthFilter here once auth/ module is implemented
    // private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth ->
                // TODO: Replace with proper rules once JwtAuthFilter is wired in.
                //   Public:  POST /api/auth/**, GET /api/hotels/**, GET /api/search/**
                //   Host:    /api/host/**
                //   Admin:   /api/admin/**
                //   Else:    authenticated()
                auth.anyRequest().permitAll()
            );
            // TODO: Add this once JwtAuthFilter exists:
            // .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

        return http.build();
    }

}
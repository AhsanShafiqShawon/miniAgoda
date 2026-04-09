package com.miniagoda.common.config;

import com.miniagoda.common.filter.JwtAuthenticationFilter;
import com.miniagoda.common.security.PublicRoutes;
import com.miniagoda.common.util.SecurityErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {

                for (String[] matcher : PublicRoutes.MATCHERS) {
                    auth.requestMatchers(HttpMethod.valueOf(matcher[0]), matcher[1]).permitAll();
                }

                auth
                    .requestMatchers("/api/host/**").hasRole("HOST")
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated();
            })
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    securityErrorWriter.write(response, request, 401, "Unauthorized",
                        "Authentication is required to access this resource."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    securityErrorWriter.write(response, request, 403, "Forbidden",
                        "You do not have permission to perform this action."))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
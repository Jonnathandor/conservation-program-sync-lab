package com.jonnathangarcia.conservationsync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    InMemoryUserDetailsManager demoUser(@Value("${meadowline.demo.password:}") String password) {
        if (password.isBlank()) {
            throw new IllegalStateException("Set MEADOWLINE_DEMO_PASSWORD for the local demo account.");
        }
        return new InMemoryUserDetailsManager(User.withUsername("demo")
                .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password)).roles("DEMO_USER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/error").permitAll()
                        .anyRequest().hasRole("DEMO_USER"))
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        (request, response, exception) -> response.sendError(401),
                        request -> request.getServletPath().startsWith("/api/")))
                .formLogin(login -> login.defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                // Keep Spring's session-backed CSRF protection and session fixation protection.
                .build();
    }
}

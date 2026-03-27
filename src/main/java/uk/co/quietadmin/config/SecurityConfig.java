package uk.co.quietadmin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import uk.co.quietadmin.security.JwtAuthenticationFilter;
import uk.co.quietadmin.security.RestAccessDeniedHandler;
import uk.co.quietadmin.security.RestAuthenticationEntryPoint;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RestAuthenticationEntryPoint entryPoint;
    private final RestAccessDeniedHandler deniedHandler;

    // ==========================================
    // PASSWORD ENCODER
    // ==========================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ==========================================
    // CORS CONFIG
    // ==========================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174",
                "https://quietadmin.co.uk",
                "https://www.quietadmin.co.uk"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        // 🔒 Important: expose only what you need (optional)
        config.setExposedHeaders(List.of("Set-Cookie"));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ==========================================
    // SECURITY FILTER CHAIN
    // ==========================================

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ---------------------------
                // Core settings
                // ---------------------------
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ---------------------------
                // Exception handling
                // ---------------------------
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )

                // ---------------------------
                // Authorisation rules
                // ---------------------------
                .authorizeHttpRequests(auth -> auth

                        // Preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ===== PUBLIC AUTH =====
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/verify",
                                "/api/auth/refresh",
                                "/api/auth/resend-verification",
                                "/api/auth/set-password",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm",
                                "/api/stripe/webhook"
                        ).permitAll()

                        // ===== SWAGGER =====
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // ===== EVERYTHING ELSE UNDER /api =====
                        .requestMatchers("/api/**").authenticated()

                        // ===== NON API =====
                        .anyRequest().permitAll()
                )

                // ---------------------------
                // JWT Filter
                // ---------------------------
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // ---------------------------
                // Disable unused auth
                // ---------------------------
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
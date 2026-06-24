package com.shop.config;

import com.shop.modules.user.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.shop.auth.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return phone -> {
            com.shop.modules.user.User user = userRepository.findByPhone(phone)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + phone));
            return User.builder()
                    .username(user.getPhone())
                    .password(user.getPasswordHash())
                    .roles(user.getRole().name())
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                // ── Security Headers ─────────────────────────────────────
                .headers(headers -> {
                    headers.frameOptions(frame -> frame.deny());
                    headers.contentTypeOptions(cto -> {}); // X-Content-Type-Options: nosniff
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .maxAgeInSeconds(31536000)
                            .includeSubDomains(true));
                    headers.referrerPolicy(ref -> ref
                            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions -> permissions.policy("geolocation=(), camera=(), microphone=()"));
                    headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"));
                    headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"));
                    headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Cross-Origin-Embedder-Policy", "require-corp"));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                            "font-src 'self' https://fonts.gstatic.com data:; " +
                            "img-src 'self' data: blob: https:; " +
                            "connect-src 'self' https://generativelanguage.googleapis.com wss: ws:; " +
                            "frame-ancestors 'none';"
                    ));
                })
                // ── Authorization ────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/login",
                                "/billing",
                                "/products",
                                "/customers",
                                "/whatsapp",
                                "/salesmen",
                                "/stock",
                                "/khata",
                                "/expenses",
                                "/damage",
                                "/areas",
                                "/deliveries",
                                "/users",
                                "/schedulers",
                                "/api/auth/**",
                                "/api/config/**",
                                "/api/deliveries/whatsapp-reply",
                                "/ws/**",
                                "/actuator/health",   // Only health check is public
                                "/error"
                        ).permitAll()
                        // Swagger — accessible only when not on prod profile, or by ADMIN
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-ui/index.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/swagger-resources",
                                "/webjars/**"
                        ).hasRole("ADMIN")
                        // Actuator restricted to ADMIN only
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter,
                        UsernamePasswordAuthenticationFilter.class)
                // ── Return 401 (not 403) when no token is provided ───────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (request, response, authException) -> {
                                    response.setStatus(
                                            jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType("application/json");
                                    response.getWriter().write(
                                            "{\"success\":false,\"message\":\"Authentication required. Please login.\",\"data\":null}");
                                })
                );
        return http.build();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:8085",
                "http://localhost:8086",
                "http://localhost:5173",
                "http://localhost",
                "http://localhost:*",
                "https://localhost",
                "https://localhost:*",
                "capacitor://localhost",
                "capacitor://*",
                "app://localhost",
                "app://*",
                "https://*.trycloudflare.com",
                "https://uat.laritraders.store",
                "https://app.laritraders.store",
                "https://*.laritraders.store",
                "https://*.pages.dev"
        ));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
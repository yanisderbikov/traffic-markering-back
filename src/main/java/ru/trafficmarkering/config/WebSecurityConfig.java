package ru.trafficmarkering.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    @Value("${allowed.origins}")
    private List<String> allowedOrigins;

    public WebSecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // ── Доска объявлений и карточки: видны всем, даже неавторизованным ──
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                    // ── Межсервисные ручки: общий секрет SERVICE_JWT_TOKEN ──
                    .requestMatchers("/api/actuator/**").hasRole("SERVICE")
                    .requestMatchers("/api/tech/**").hasAnyRole("SERVICE", "ADMIN")
                    .requestMatchers("/api/social/callback/**").permitAll()
                    // ── Пользовательские роли ──
                    .requestMatchers("/api/campaigns/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .requestMatchers("/api/files/**").hasAnyRole("CUSTOMER", "ADMIN")
                    .requestMatchers("/api/applications/**").hasAnyRole("CREATOR", "CUSTOMER", "ADMIN")
                    .requestMatchers("/api/profile/**").hasAnyRole("CREATOR", "CUSTOMER", "ADMIN")
                    .requestMatchers("/api/social/**").hasAnyRole("CREATOR", "ADMIN")
                    .requestMatchers("/api/auth/me").authenticated()
                    .anyRequest().permitAll()
            )
            // Точка входа заведена ради актуатора: ему нужен 401 с JSON-телом, а не пустой 403.
            // Правило одно, поэтому Spring Security делает его точкой входа всей цепочки —
            // любой неавторизованный запрос получает 401 {"error":"invalid auth token"}.
            // Фронту это подходит: в приватной зоне он одинаково уводит на логин и с 401, и с 403
            .exceptionHandling(e -> e
                    .defaultAuthenticationEntryPointFor(unauthorizedEntryPoint(),
                            new AntPathRequestMatcher("/api/actuator/**")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"invalid auth token\"}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(false); // Не требуется: токен ходит заголовком, не кукой

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

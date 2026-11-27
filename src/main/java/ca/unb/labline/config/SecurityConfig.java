package ca.unb.labline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Who may call which URL (FR2 / NFR4).
 * Web pages (static HTML/CSS/JS) are public; every /api call needs a login.
 * Clients log in with HTTP Basic (email + password on each request);
 * no server session is kept, so CSRF protection is not needed.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/favicon.ico")
                                .permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/instructor/**").hasAnyRole("INSTRUCTOR", "ADMIN")
                        .requestMatchers("/api/ta/**").hasAnyRole("TA", "INSTRUCTOR")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorized()))
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized()));
        return http.build();
    }

    /**
     * Answers 401 without the "WWW-Authenticate: Basic" header, so the browser
     * does not show its own login pop-up; our login page handles it instead.
     */
    private static AuthenticationEntryPoint unauthorized() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    /** bcrypt: slow, salted hashing, so stolen hashes are hard to crack. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

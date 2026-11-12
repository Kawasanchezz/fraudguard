package com.fraudguard.config;

import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Usuários em memória com HTTP Basic. O usuário SERVICE é chamado a cada transação e usa
 * comparação direta (um hash BCrypt custaria ~200 ms por requisição e estoura o p95);
 * ANALISTA e ADMIN, de baixo volume, usam BCrypt.
 */
@Configuration
public class SecurityConfig {

    /** Só recursos do próprio app: o simulador não carrega nem executa nada de fora (nem script inline). */
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(repositorioCsrf())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/fraud/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/csrf").permitAll()
                        .requestMatchers("/simulador", "/simulador/**").permitAll()
                        .requestMatchers("/fraud/**").hasRole("SERVICE")
                        .requestMatchers("/cases/**").hasRole("ANALISTA")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * Token CSRF em cookie (HttpOnly, SameSite=Strict) comparado com o cabeçalho X-XSRF-TOKEN, valendo para
     * /cases e /admin (usados por navegador). /fraud fica de fora: é chamada de serviço para serviço, sem navegador.
     */
    private static CookieCsrfTokenRepository repositorioCsrf() {
        CookieCsrfTokenRepository repositorio = new CookieCsrfTokenRepository();
        repositorio.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Strict").path("/"));
        return repositorio;
    }

    @Bean
    @SuppressWarnings("deprecation")
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of(
                "bcrypt", new BCryptPasswordEncoder(),
                "noop", NoOpPasswordEncoder.getInstance()));
    }

    /**
     * Devolve um lambda, não o InMemoryUserDetailsManager: este implementa UserDetailsPasswordService,
     * e o Spring o usaria para regravar a senha {noop} em BCrypt no primeiro login, trazendo de volta
     * o custo de hash a cada requisição.
     */
    @Bean
    UserDetailsService users(FraudProperties props, PasswordEncoder encoder) {
        var sec = props.security();
        var manager = new InMemoryUserDetailsManager(
                User.withUsername("service").password("{noop}" + sec.servicePassword()).roles("SERVICE").build(),
                User.withUsername("analista").password(encoder.encode(sec.analystPassword())).roles("ANALISTA").build(),
                User.withUsername("admin").password(encoder.encode(sec.adminPassword())).roles("ADMIN").build());
        return manager::loadUserByUsername;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

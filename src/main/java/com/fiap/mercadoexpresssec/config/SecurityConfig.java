package com.fiap.mercadoexpresssec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String PARAM_EMAIL = "email";
    public static final String PARAM_SENHA = "senha";

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;

    public SecurityConfig(LoginSuccessHandler loginSuccessHandler, LoginFailureHandler loginFailureHandler) {
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
    }

    /**
     * Parte 2 - API REST (/api/**) usada no Postman/Insomnia: HTTP Basic com os mesmos usuarios
     * da tabela TDS_Users_Mercado, sem sessao e sem CSRF (nao ha formulario nem cookie).
     * Sem credenciais a resposta e 401, em vez de redirecionar para a tela de login.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("ADMIN", "CLIENTE"))
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Rotas publicas: landing page, login, cadastro (Sign Up), recursos estaticos
                        .requestMatchers("/", "/index", "/login", "/cadastro", "/acesso-negado", "/error").permitAll()
                        .requestMatchers("/css/**", "/img/**", "/webjars/**", "/favicon.ico").permitAll()
                        // Somente ADMIN: painel, dashboard de vendas e operacoes de escrita do CRUD
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/mercado/novo", "/mercado/*/editar", "/mercado/*/excluir").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/mercado/**").hasRole("ADMIN")
                        // ADMIN e CLIENTE: consulta de produtos (Read)
                        .requestMatchers(HttpMethod.GET, "/mercado", "/mercado/*").hasAnyRole("ADMIN", "CLIENTE")
                        // Parte 2 - ADMIN e CLIENTE: carrinho, finalizacao da compra e "Minhas compras"
                        .requestMatchers("/carrinho/**", "/pedidos/**").hasAnyRole("ADMIN", "CLIENTE")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter(PARAM_EMAIL)
                        .passwordParameter(PARAM_SENHA)
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedPage("/acesso-negado"));

        return http.build();
    }

}

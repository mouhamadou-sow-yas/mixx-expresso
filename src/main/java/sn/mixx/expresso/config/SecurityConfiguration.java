package sn.mixx.expresso.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import sn.mixx.expresso.security.AuthoritiesConstants;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import tech.jhipster.config.JHipsterProperties;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(ReactiveUserDetailsService userDetailsService) {
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager =
            new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder());
        return authenticationManager;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .securityMatcher(
                new NegatedServerWebExchangeMatcher(
                    new OrServerWebExchangeMatcher(
                        pathMatchers("/app/**", "/i18n/**", "/content/**", "/swagger-ui/**",
                            "/index.html", "/*.js", "/*.css", "/*.ico", "/*.svg", "/*.map", "/*.txt")
                    )
                )
            )
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(jHipsterProperties.getSecurity().getContentSecurityPolicy()))
                    .frameOptions(frameOptions -> frameOptions.mode(Mode.DENY))
                    .referrerPolicy(referrer ->
                        referrer.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    )
                    .permissionsPolicy(permissions ->
                        permissions.policy(
                            "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                        )
                    )
            )
            .authorizeExchange(authz ->
                    authz
                        .pathMatchers("/api/authenticate").permitAll()
                        .pathMatchers("/api/authenticate/verify-otp").permitAll()
                        .pathMatchers("/api/register").permitAll()
                        .pathMatchers("/api/activate").permitAll()
                        .pathMatchers("/api/account/reset-password/init").permitAll()
                        .pathMatchers("/api/account/reset-password/finish").permitAll()
                        .pathMatchers("/api/partners/**").permitAll()
                        .pathMatchers("/api/admin/**").hasAnyAuthority(AuthoritiesConstants.ADMIN, AuthoritiesConstants.OPERATOR_BO, AuthoritiesConstants.SUPERVISOR)
                        .pathMatchers("/api/**").authenticated()
                        .pathMatchers("/v1/bundles/**").permitAll()
                        .pathMatchers("/v1/transactions/**").permitAll()
                        .pathMatchers("/v1/clients/**").permitAll()
                        .pathMatchers("/v1/**").authenticated()
                        .pathMatchers("/bo/**").authenticated()
                        .pathMatchers("/services/**").authenticated()
                        .pathMatchers("/v3/api-docs/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        .pathMatchers("/management/health").permitAll()
                        .pathMatchers("/management/health/**").permitAll()
                        .pathMatchers("/management/info").permitAll()
                        .pathMatchers("/management/prometheus").permitAll()
                        .pathMatchers("/management/**").hasAuthority(AuthoritiesConstants.ADMIN)
                        .pathMatchers("/").permitAll()
                        .pathMatchers("/*.*").permitAll()
            )
            .httpBasic(basic -> basic.disable())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }
}
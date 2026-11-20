package com.hospital.appointment.identity.infrastructure;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.List;

@Configuration
@EnableMethodSecurity
class IdentitySecurityConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    RSAKey identityRsaKey(IdentityProperties properties, Environment environment) throws Exception {
        boolean development = environment.matchesProfiles("dev");
        if (properties.privateKey() == null || properties.privateKey().isBlank()) {
            if (!development) {
                throw new IllegalStateException("Set IDENTITY_PRIVATE_KEY_PEM and IDENTITY_PUBLIC_KEY_PEM outside the dev profile.");
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(properties.keyId()).algorithm(JWSAlgorithm.RS256).build();
        }
        RSAKey signingKey = RSAKey.parseFromPEMEncodedObjects(properties.privateKey()).toRSAKey();
        RSAKey publicKey = properties.publicKey() == null || properties.publicKey().isBlank()
                ? signingKey.toPublicJWK() : RSAKey.parseFromPEMEncodedObjects(properties.publicKey()).toRSAKey();
        return new RSAKey.Builder(publicKey.toRSAPublicKey()).privateKey(signingKey.toRSAPrivateKey())
                .keyID(properties.keyId()).algorithm(JWSAlgorithm.RS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey identityRsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(identityRsaKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey identityRsaKey, IdentityProperties properties) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(identityRsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new IdentityJwtValidator(properties));
        return decoder;
    }

    @Bean
    JWKSet publicIdentityJwks(RSAKey identityRsaKey) {
        return new JWKSet(identityRsaKey.toPublicJWK());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityProperties properties, AccountStateFilter accountStateFilter,
                                             OriginValidationFilter originValidationFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(properties.cookieSecure()).sameSite("Strict"));
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/.well-known/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"),
                                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**")))
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(false))
                .headers(headers -> headers.contentTypeOptions(options -> { })
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint())
                        .accessDeniedHandler(new ProblemAccessDeniedHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/api/v1/system/info", "/actuator/health/**", "/actuator/info",
                                "/.well-known/jwks.json", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(token -> {
                    String role = token.getClaimAsString("role");
                    if (role == null || token.getClaim("authz_version") == null) {
                        throw new org.springframework.security.oauth2.jwt.JwtValidationException("Missing authorization claims", List.of());
                    }
                    return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)), token.getSubject());
                })).authenticationEntryPoint(new ProblemAuthenticationEntryPoint()))
                .addFilterBefore(originValidationFilter, org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterAfter(accountStateFilter, AuthorizationFilter.class);
        return http.build();
    }
}

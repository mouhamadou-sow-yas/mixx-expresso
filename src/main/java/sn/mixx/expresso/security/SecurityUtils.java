package sn.mixx.expresso.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.Arrays;

public final class SecurityUtils {

    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;
    public static final String AUTHORITIES_CLAIM = "auth";
    public static final String USER_ID_CLAIM = "userId";

    private SecurityUtils() {}

    public static Mono<String> getCurrentUserLogin() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .flatMap(authentication -> Mono.justOrEmpty(extractPrincipal(authentication)));
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) return null;
        if (authentication.getPrincipal() instanceof UserDetails u) return u.getUsername();
        if (authentication.getPrincipal() instanceof Jwt jwt) return jwt.getSubject();
        if (authentication.getPrincipal() instanceof String s) return s;
        return null;
    }

    public static Mono<String> getCurrentUserJWT() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(auth -> auth.getCredentials() instanceof String)
            .map(auth -> (String) auth.getCredentials());
    }

    public static Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(auth -> auth.getPrincipal() instanceof ClaimAccessor)
            .map(auth -> (ClaimAccessor) auth.getPrincipal())
            .mapNotNull(principal -> {
                Object claim = principal.getClaim(USER_ID_CLAIM);
                if (claim instanceof Long l) return l;
                if (claim instanceof Number n) return n.longValue();
                return null;
            });
    }

    public static Mono<Boolean> isAuthenticated() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getAuthorities)
            .map(authorities -> authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(AuthoritiesConstants.ANONYMOUS::equals));
    }

    public static Mono<Boolean> hasCurrentUserAnyOfAuthorities(String... authorities) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getAuthorities)
            .map(authorityList -> authorityList.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> Arrays.asList(authorities).contains(authority)));
    }

    public static Mono<Boolean> hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }
}
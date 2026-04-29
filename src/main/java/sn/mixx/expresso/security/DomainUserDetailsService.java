package sn.mixx.expresso.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.User;
import sn.mixx.expresso.repository.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    @Override
    public Mono<UserDetails> findByUsername(String login) {
        log.debug("Authenticating {}", login);
        String lowercaseLogin = login.toLowerCase(Locale.ENGLISH);
        return userRepository.findOneWithAuthoritiesByLogin(lowercaseLogin)
            .switchIfEmpty(Mono.error(new UsernameNotFoundException("User " + lowercaseLogin + " was not found")))
            .filter(user -> user.isActivated())
            .switchIfEmpty(Mono.error(new UsernameNotFoundException("User " + lowercaseLogin + " was not activated")))
            .map(user -> UserWithId.fromUser(user));
    }

    public static class UserWithId extends org.springframework.security.core.userdetails.User {

        private static final long serialVersionUID = 1L;
        private final Long id;

        public UserWithId(String username, String password, List<SimpleGrantedAuthority> authorities, Long id) {
            super(username, password, authorities);
            this.id = id;
        }

        public Long getId() { return id; }

        public static UserWithId fromUser(User user) {
            List<SimpleGrantedAuthority> grantedAuthorities = user.getAuthorities()
                .stream()
                .map(authority -> new SimpleGrantedAuthority(authority.getName()))
                .collect(Collectors.toList());
            return new UserWithId(user.getLogin(), user.getPassword(), grantedAuthorities, user.getId());
        }
    }
}
package sn.mixx.expresso.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.repository.UserRepository;
import sn.mixx.expresso.security.AccountLockedException;
import sn.mixx.expresso.security.AuthoritiesConstants;
import sn.mixx.expresso.security.DomainUserDetailsService.UserWithId;
import sn.mixx.expresso.service.LoginAttemptService;
import sn.mixx.expresso.service.dto.mfa.MfaAuthenticationResponse;
import sn.mixx.expresso.service.dto.mfa.OtpVerificationRequest;
import sn.mixx.expresso.service.mfa.OtpService;
import sn.mixx.expresso.service.mfa.SmsService;
import sn.mixx.expresso.web.rest.errors.mfa.MfaErrorCode;
import sn.mixx.expresso.web.rest.errors.mfa.MfaException;
import sn.mixx.expresso.web.rest.vm.LoginVM;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import static sn.mixx.expresso.security.SecurityUtils.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class AuthenticateController {

    private final JwtEncoder jwtEncoder;
    private final ReactiveAuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final SmsService smsService;
    private final LoginAttemptService loginAttemptService;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds:0}")
    private long tokenValidityInSeconds;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me:0}")
    private long tokenValidityInSecondsForRememberMe;

    public AuthenticateController(JwtEncoder jwtEncoder,
                                   ReactiveAuthenticationManager authenticationManager,
                                   UserRepository userRepository,
                                   OtpService otpService,
                                   SmsService smsService,
                                   LoginAttemptService loginAttemptService) {
        this.jwtEncoder = jwtEncoder;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.smsService = smsService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/authenticate")
    public Mono<ResponseEntity<MfaAuthenticationResponse>> authorize(@Valid @RequestBody Mono<LoginVM> loginVM) {
        return loginVM.flatMap(login ->
            loginAttemptService.checkAccountLocked(login.getUsername())
                .then(authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login.getUsername(), login.getPassword())))
                .flatMap(auth -> loginAttemptService.resetFailedAttemptsAndGet(login.getUsername())
                    .flatMap(user -> {
                        boolean isFinance = user.getAuthorities().stream()
                            .anyMatch(a -> a.getName().equals(AuthoritiesConstants.FINANCE));
                        if (isFinance && (!user.isMfaEnabled() || user.getPhoneNumber() == null)) {
                            return Mono.error(new MfaException(MfaErrorCode.MFA_REQUIRED_FOR_ROLE,
                                "Le MFA est obligatoire pour le profil Finance."));
                        }
                        if (user.isMfaEnabled() && user.getPhoneNumber() != null) {
                            return handleMfaAuthentication(user, auth, login.isRememberMe());
                        } else {
                            String jwt = createToken(auth, login.isRememberMe());
                            return Mono.just(ResponseEntity.ok(MfaAuthenticationResponse.authenticated(jwt)));
                        }
                    })
                )
                .onErrorResume(BadCredentialsException.class, ex ->
                    loginAttemptService.registerFailedAttempt(login.getUsername())
                        .then(loginAttemptService.getRemainingAttempts(login.getUsername()))
                        .flatMap(remaining -> {
                            if (remaining <= 0) {
                                return Mono.error(new AccountLockedException("Compte verrouillé pour 30 minutes.", 30));
                            }
                            return Mono.error(new BadCredentialsException(
                                "Identifiants invalides. " + remaining + " tentative(s) restante(s)."));
                        })
                )
        );
    }

    private Mono<ResponseEntity<MfaAuthenticationResponse>> handleMfaAuthentication(
        sn.mixx.expresso.domain.User user, Authentication auth, boolean rememberMe) {
        String otpCode = otpService.generateOtpCode();
        String maskedPhone = smsService.maskPhoneNumber(user.getPhoneNumber());
        return otpService.createOtpSession(user.getLogin(), otpCode)
            .flatMap(mfaToken -> smsService.sendOtp(user.getPhoneNumber(), otpCode)
                .flatMap(sent -> {
                    if (!sent) {
                        log.error("Échec envoi OTP pour {} au {}", user.getLogin(), maskedPhone);
                        return Mono.error(new MfaException(MfaErrorCode.OTP_SEND_FAILED,
                            "Impossible d'envoyer le code OTP. Veuillez réessayer."));
                    }
                    log.debug("OTP envoyé pour {} au {}", user.getLogin(), maskedPhone);
                    return Mono.just(ResponseEntity.ok(MfaAuthenticationResponse.mfaRequired(mfaToken, maskedPhone)));
                })
            );
    }

    @PostMapping("/authenticate/verify-otp")
    public Mono<ResponseEntity<MfaAuthenticationResponse>> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        return otpService.validateOtp(request.getMfaToken(), request.getOtpCode())
            .flatMap(username -> {
                if (username == null || username.isBlank()) {
                    return Mono.error(new MfaException(MfaErrorCode.MFA_SESSION_INVALID, "Session OTP invalide."));
                }
                return userRepository.findOneWithAuthoritiesByLogin(username);
            })
            .flatMap(user -> {
                UserWithId userWithId = UserWithId.fromUser(user);
                Authentication auth = new UsernamePasswordAuthenticationToken(userWithId, null, userWithId.getAuthorities());
                String jwt = createToken(auth, false);
                return Mono.just(ResponseEntity.ok(MfaAuthenticationResponse.authenticated(jwt)));
            });
    }

    @GetMapping("/authenticate")
    public Mono<ResponseEntity<?>> isAuthenticated(Principal principal) {
        if (principal == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return Mono.just(ResponseEntity.noContent().build());
    }

    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(" "));

        Instant now = Instant.now();
        Instant validity = rememberMe
            ? now.plus(tokenValidityInSecondsForRememberMe, ChronoUnit.SECONDS)
            : now.plus(tokenValidityInSeconds, ChronoUnit.SECONDS);

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(authentication.getName())
            .claim(AUTHORITIES_CLAIM, authorities);

        if (authentication.getPrincipal() instanceof UserWithId user) {
            builder.claim(USER_ID_CLAIM, user.getId());
        }

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, builder.build())).getTokenValue();
    }
}
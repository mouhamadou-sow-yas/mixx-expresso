package sn.mixx.expresso.aop.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@Aspect
public class LoggingAspect {

    private final Environment env;

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    @Pointcut(
        "within(@org.springframework.stereotype.Repository *)" +
        " || within(@org.springframework.stereotype.Service *)" +
        " || within(@org.springframework.web.bind.annotation.RestController *)"
    )
    public void springBeanPointcut() {}

    @Pointcut(
        "within(sn.mixx.expresso.repository..*)" +
        " || within(sn.mixx.expresso.service..*)" +
        " || within(sn.mixx.expresso.web.rest..*)"
    )
    public void applicationPackagePointcut() {}

    private Logger logger(JoinPoint joinPoint) {
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringTypeName());
    }

    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        logger(joinPoint).error(
            "Exception in {}() with cause = '{}' and exception = '{}'",
            joinPoint.getSignature().getName(),
            e.getCause() != null ? e.getCause() : "NULL",
            e.getMessage(),
            e
        );
    }

    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        Logger log = logger(joinPoint);
        if (log.isDebugEnabled()) {
            log.debug("Enter: {}() with argument[s] = {}", joinPoint.getSignature().getName(), Arrays.toString(joinPoint.getArgs()));
        }
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Mono<?> mono) {
                return mono.doOnSuccess(r -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Exit: {}() with result = {}", joinPoint.getSignature().getName(), r);
                    }
                });
            } else if (result instanceof Flux<?> flux) {
                return flux.doOnComplete(() -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Exit: {}() Flux completed", joinPoint.getSignature().getName());
                    }
                });
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Exit: {}() with result = {}", joinPoint.getSignature().getName(), result);
                }
                return result;
            }
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument: {} in {}()", Arrays.toString(joinPoint.getArgs()), joinPoint.getSignature().getName());
            throw e;
        }
    }
}
package h99.ecommerce.aspect;

import h99.ecommerce.annotation.CustomTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CustomTransactionalAspect {

    private final TransactionTemplate transactionTemplate;

    @Around("@annotation(customTransactional)")
    public Object executeInTransaction(ProceedingJoinPoint joinPoint, CustomTransactional customTransactional) throws Throwable {
        log.debug("트랜잭션 시작");

        return transactionTemplate.execute(status -> {
            try {
                Object result = joinPoint.proceed();
                log.debug("트랜잭션 커밋 - method: {}", joinPoint.getSignature().getName());
                return result;
            } catch (Throwable e) {
                status.setRollbackOnly();
                log.error("트랜잭션 롤백 - method: {}", joinPoint.getSignature().getName(), e);

                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
        });
    }
}

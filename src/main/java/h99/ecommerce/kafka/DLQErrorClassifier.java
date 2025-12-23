package h99.ecommerce.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * DLQ 에러 분류기
 * 재처리 가능 여부를 판단
 */
@Slf4j
@Component
public class DLQErrorClassifier {

    /**
     * 재처리 가능한 에러인지 판단
     */
    public boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }

        // 일시적 장애 (재시도 가능)
        if (isTransientError(error)) {
            log.info("[DLQ Classifier] 재시도 가능한 에러 - {}", error.getClass().getSimpleName());
            return true;
        }

        // 영구적 장애 (재시도 불가)
        if (isPermanentError(error)) {
            log.warn("[DLQ Classifier] 재시도 불가능한 에러 - {}", error.getClass().getSimpleName());
            return false;
        }

        // 원인(cause) 확인
        if (error.getCause() != null) {
            return isRetryable(error.getCause());
        }

        // 기본: 재시도 불가
        log.warn("[DLQ Classifier] 알 수 없는 에러, 재시도 불가 - {}", error.getClass().getName());
        return false;
    }

    /**
     * 일시적 에러 (네트워크, 타임아웃, 락 등)
     */
    private boolean isTransientError(Throwable error) {
        return error instanceof OptimisticLockingFailureException
            || error instanceof PessimisticLockingFailureException
            || error instanceof org.springframework.dao.TransientDataAccessResourceException
            || error instanceof org.springframework.dao.QueryTimeoutException
            || error instanceof java.net.SocketTimeoutException
            || error instanceof java.net.ConnectException
            || error.getMessage() != null && error.getMessage().contains("timeout");
    }

    /**
     * 영구적 에러 (비즈니스 로직 위반, 데이터 무결성 등)
     */
    private boolean isPermanentError(Throwable error) {
        return error instanceof IllegalArgumentException
            || error instanceof IllegalStateException
            || error instanceof DataIntegrityViolationException
            || error instanceof NullPointerException;
    }

    /**
     * 에러 타입 분류
     */
    public ErrorType classifyError(Throwable error) {
        if (error == null) {
            return ErrorType.UNKNOWN;
        }

        if (isTransientError(error)) {
            return ErrorType.TRANSIENT;
        }

        if (isPermanentError(error)) {
            return ErrorType.PERMANENT;
        }

        if (error.getCause() != null) {
            return classifyError(error.getCause());
        }

        return ErrorType.UNKNOWN;
    }

    public enum ErrorType {
        TRANSIENT,   // 일시적 장애 (재시도 가능)
        PERMANENT,   // 영구적 장애 (재시도 불가)
        UNKNOWN      // 알 수 없음
    }
}
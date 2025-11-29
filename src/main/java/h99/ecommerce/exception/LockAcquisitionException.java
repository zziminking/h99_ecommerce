package h99.ecommerce.exception;

public class LockAcquisitionException extends RuntimeException {

    private final String lockKey;
    private final long waitTime;

    public LockAcquisitionException(String message, String lockKey, long waitTime) {
        super(String.format("락 획득 실패: lockKey=%s, waitTime=%ds", lockKey, waitTime));
        this.lockKey = lockKey;
        this.waitTime = waitTime;
    }

    private String getLockKey() {
        return lockKey;
    }

    private long getWaitTime() {
        return waitTime;
    }
}

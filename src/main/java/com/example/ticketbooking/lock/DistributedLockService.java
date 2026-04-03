package com.example.ticketbooking.lock;

import java.util.concurrent.TimeUnit;

/**
 * Strategy interface for distributed locking.
 * Variant 1 → RedissonLockService (single Redis)
 * Variant 2 → RedlockLockService (multi Redis, added later)
 */
public interface DistributedLockService {

    /**
     * Acquire lock for the given key.
     *
     * @param lockKey   unique lock identifier, e.g. "ticket:lock:E1:S1"
     * @param leaseTime max time to hold the lock automatically
     * @param unit      time unit for leaseTime
     * @return true if lock acquired, false otherwise
     */
    boolean tryLock(String lockKey, long leaseTime, TimeUnit unit);

    /**
     * Release the lock for the given key.
     */
    void unlock(String lockKey);
}

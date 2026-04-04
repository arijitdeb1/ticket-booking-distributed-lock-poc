package com.example.ticketbooking.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Variant 1: Single Redis distributed lock using Redisson RLock.
 * Active when profile is NOT "redlock" (i.e. default profile).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!redlock")
public class RedissonLockService implements DistributedLockService {

    private final RedissonClient redissonClient;

    @Override
    public boolean tryLock(String lockKey, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // waitTime=0 → fail immediately if lock is held by someone else
            boolean acquired = lock.tryLock(0, leaseTime, unit);
            if (acquired) {
                log.debug("Lock acquired: {}", lockKey);
            } else {
                log.debug("Lock NOT acquired (held by another): {}", lockKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring lock: {}", lockKey);
            return false;
        }
    }

    @Override
    public void renewLease(String lockKey, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            // Reentrant call — resets the TTL to a fresh leaseTime from now
            lock.lock(leaseTime, unit);
            log.info("Lock lease renewed: key={}, newLease={} {}", lockKey, leaseTime, unit);
        } else {
            log.warn("Cannot renew lease — lock not held by current thread: {}", lockKey);
        }
    }

    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released: {}", lockKey);
        }
    }
}

package com.ssafy.githubble.domain.ai.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class IngestionLockRegistry {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void acquire(String archId) {
        locks.computeIfAbsent(archId, k -> new ReentrantLock()).lock();
    }

    public void release(String archId) {
        ReentrantLock lock = locks.get(archId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public boolean tryAcquire(String archId) {
        return locks.computeIfAbsent(archId, k -> new ReentrantLock()).tryLock();
    }
}

package warehouse;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Tick {
    private static final AtomicLong tick = new AtomicLong(0);
    private static long TICK_TIME_SIZE_MS = 50; // default 50ms per tick
    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition tickAdvanced = lock.newCondition();

    public static long current() {
        return tick.get();
    }

    public static void increment() {
        lock.lock();
        try {
            tick.incrementAndGet();
            tickAdvanced.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public static void setTickTimeSize(long ms) {
        TICK_TIME_SIZE_MS = ms;
    }

    /**
     * Wait for the specified number of simulation ticks to pass.
     * This does not use real time, but advances with the simulation's tick thread.
     */
    public static void sleepTicks(long ticks) throws InterruptedException {
        lock.lock();
        try {
            long target = tick.get() + ticks;
            while (tick.get() < target) {
                tickAdvanced.await();
            }
        } finally {
            lock.unlock();
        }
    }
}

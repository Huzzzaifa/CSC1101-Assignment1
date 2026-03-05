package warehouse;

import java.util.concurrent.atomic.AtomicLong;

public class Tick {
    private static final AtomicLong tick = new AtomicLong(0);
    private static long TICK_TIME_SIZE_MS = 50; // default 50ms per tick

    public static long current() {
        return tick.get();
    }

    public static void increment() {
        tick.incrementAndGet();
    }

    public static void setTickTimeSize(long ms) {
        TICK_TIME_SIZE_MS = ms;
    }

    public static void sleepTicks(long ticks) throws InterruptedException {
        Thread.sleep(ticks * TICK_TIME_SIZE_MS);
    }
}

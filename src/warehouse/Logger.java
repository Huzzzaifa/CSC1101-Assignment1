package warehouse;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

public class Logger {
    private static final PrintStream out = System.out;
    private static final AtomicInteger pickCounter = new AtomicInteger(0);

    public static int nextPickId() {
        return pickCounter.incrementAndGet();
    }

    public static synchronized void log(String tid, String event, String details) {
        long t = Tick.current();
        out.printf("tick=%d tid=%s event=%s %s\n", t, tid, event, details);
    }
}

package warehouse;

import java.util.concurrent.Semaphore;
import java.util.LinkedList;
import java.util.Queue;

public class TrolleyPool {
    private final Semaphore permits;
    private final Queue<Trolley> free = new LinkedList<>();

    public TrolleyPool(int k) {
        permits = new Semaphore(k, true);
        for (int i = 1; i <= k; i++) {
            free.add(new Trolley(i));
        }
    }

    public Trolley acquire() throws InterruptedException {
        permits.acquire();
        synchronized (free) {
            return free.remove();
        }
    }

    public void release(Trolley t) {
        synchronized (free) {
            free.add(t);
        }
        permits.release();
    }
}

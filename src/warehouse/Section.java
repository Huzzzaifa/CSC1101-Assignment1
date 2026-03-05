package warehouse;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Section {
    public final String name;
    private final int capacity;
    private int count;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public Section(String name, int capacity, int initial) {
        this.name = name;
        this.capacity = capacity;
        this.count = initial;
    }

    /**
     * Acquire exclusive access to this section (for stocking or picking).
     */
    public void lockSection() {
        lock.lock();
    }

    public void unlockSection() {
        lock.unlock();
    }

    /**
     * expose waiting condition for pickers
     */
    public Condition notEmpty() {
        return notEmpty;
    }

    /**
     * Wait until at least one box is available, then take one and return true.
     * Caller must hold the section lock.
     */
    public boolean takeBox() throws InterruptedException {
        while (count == 0) {
            notEmpty.await();
        }
        count--;
        notFull.signal();
        return true;
    }

    /**
     * Try to add boxes up to requested amount. Returns number actually added.
     * Caller must hold the lock.
     */
    public int addBoxes(int amount) throws InterruptedException {
        int added = 0;
        while (added < amount) {
            while (count >= capacity) {
                notFull.await();
            }
            int space = capacity - count;
            int toAdd = Math.min(space, amount - added);
            count += toAdd;
            added += toAdd;
            notEmpty.signalAll();
            if (added < amount) {
                // Wait for more space
                // releasing lock temporarily is done by await above
            }
        }
        return added;
    }

    public int getCount() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }
}

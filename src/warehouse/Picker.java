package warehouse;

import java.util.Random;

public class Picker implements Runnable {
    private final String id;
    private final Section[] sections;
    private final TrolleyPool pool;
    private final Random rand;

    public Picker(int num, Section[] sections, TrolleyPool pool, long seed) {
        this.id = "P" + num;
        this.sections = sections;
        this.pool = pool;
        this.rand = new Random(seed);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // wait a random time before attempting next pick
                int waitTicks = 1 + rand.nextInt(20);
                Tick.sleepTicks(waitTicks);

                // choose section randomly
                Section sec = sections[rand.nextInt(sections.length)];
                int pickId = Logger.nextPickId();
                Logger.log(id, "pick_start", "pick_id=" + pickId + " section=" + sec.name.toLowerCase());

                // acquire trolley before attempting to pick
                Trolley t = pool.acquire();
                Logger.log(id, "acquire_trolley", "trolley_id=" + t.id + " waited_ticks=0");

                int waited = 0;
                // hold the trolley while waiting for a box in the chosen section
                sec.lockSection();
                try {
                    long startWait = Tick.current();
                    sec.takeBox(); // will wait on notEmpty if count == 0
                    long endWait = Tick.current();
                    waited = (int)(endWait - startWait);
                } finally {
                    sec.unlockSection();
                }

                Logger.log(id, "pick_done", "pick_id=" + pickId + " section=" + sec.name.toLowerCase() + " waited_ticks=" + waited + " trolley_id=" + t.id);
                // release trolley only after a box has been removed
                Logger.log(id, "release_trolley", "trolley_id=" + t.id);
                pool.release(t);
            }
        } catch (InterruptedException e) {
            // exit
        }
    }
}

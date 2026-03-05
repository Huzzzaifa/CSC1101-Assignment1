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

                // acquire trolley
                Trolley t = pool.acquire();
                Logger.log(id, "acquire_trolley", "trolley_id=" + t.id + " waited_ticks=0");

                int waited = 0;
                // try to take box; count waited ticks as difference in global tick counter
                sec.lockSection();
                long startWait = Tick.current();
                try {
                    sec.takeBox();
                } finally {
                    long endWait = Tick.current();
                    waited = (int)(endWait - startWait);
                    sec.unlockSection();
                }

                Logger.log(id, "pick_done", "pick_id=" + pickId + " section=" + sec.name.toLowerCase() + " waited_ticks=" + waited + " trolley_id=" + t.id);
                // release trolley
                Logger.log(id, "release_trolley", "trolley_id=" + t.id);
                pool.release(t);
            }
        } catch (InterruptedException e) {
            // exit
        }
    }
}

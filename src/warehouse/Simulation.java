package warehouse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.EnumMap;

public class Simulation {
    public static void main(String[] args) throws Exception {
        // configurable parameters
        int numStockers = 2;
        int numPickers = 5;
        int sectionCapacity = 10;
        long seed = 12345;
        long runTicks = 2000; // run for two days
        long tickTimeMs = 50;

        // sections and initial count 5 each
        Section[] sections = new Section[SectionType.values().length];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new Section(SectionType.values()[i].name().toLowerCase(), sectionCapacity, 5);
        }

        StagingArea staging = new StagingArea();
        int k = (numStockers + numPickers) / 2;
        TrolleyPool pool = new TrolleyPool(k);
        DeliveryGenerator generator = new DeliveryGenerator(staging, seed, 0.01);

        ExecutorService exec = Executors.newCachedThreadPool();
        exec.execute(generator);

        for (int i = 1; i <= numStockers; i++) {
            exec.execute(new Stocker(i, staging, sections, pool));
        }
        for (int i = 1; i <= numPickers; i++) {
            exec.execute(new Picker(i, sections, pool, seed + i));
        }

        // ticker thread
        Thread ticker = new Thread(() -> {
            try {
                while (Tick.current() < runTicks) {
                    Thread.sleep(tickTimeMs);
                    Tick.increment();
                }
                // stop everything
                exec.shutdownNow();
            } catch (InterruptedException e) {
            }
        });
        ticker.start();
        ticker.join();
        exec.awaitTermination(5, TimeUnit.SECONDS);
    }
}

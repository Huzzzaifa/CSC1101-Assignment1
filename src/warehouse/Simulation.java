package warehouse;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.EnumMap;
import java.util.Properties;
import java.io.FileInputStream;

public class Simulation {
    public static void main(String[] args) throws Exception {
        // Load config
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream("src/warehouse/config.properties")) {
            config.load(fis);
        }

        int numStockers = Integer.parseInt(config.getProperty("num_stockers", "2"));
        int numPickers = Integer.parseInt(config.getProperty("num_pickers", "5"));
        int sectionCapacity = Integer.parseInt(config.getProperty("section_capacity", "10"));
        long runTicks = Long.parseLong(config.getProperty("run_ticks", "2000"));
        long tickTimeMs = Long.parseLong(config.getProperty("tick_time_ms", "50"));
        double deliveryProb = Double.parseDouble(config.getProperty("delivery_prob_per_tick", "0.01"));
        long seed = 12345;

        // sections and initial count 5 each
        Section[] sections = new Section[SectionType.values().length];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new Section(SectionType.values()[i].name().toLowerCase(), sectionCapacity, 5);
        }

        StagingArea staging = new StagingArea();
        int k = (numStockers + numPickers) / 2;
        TrolleyPool pool = new TrolleyPool(k);
        DeliveryGenerator generator = new DeliveryGenerator(staging, seed, deliveryProb);

        ExecutorService exec = Executors.newCachedThreadPool();
        exec.execute(generator);

        int breakMin = Integer.parseInt(config.getProperty("stocker_break_min", "200"));
        int breakMax = Integer.parseInt(config.getProperty("stocker_break_max", "300"));
        int breakDuration = Integer.parseInt(config.getProperty("stocker_break_duration", "150"));
        Stocker[] stockers = new Stocker[numStockers];
        Picker[] pickers = new Picker[numPickers];
        for (int i = 1; i <= numStockers; i++) {
            stockers[i-1] = new Stocker(i, staging, sections, pool, breakMin, breakMax, breakDuration);
            exec.execute(stockers[i-1]);
        }
        for (int i = 1; i <= numPickers; i++) {
            pickers[i-1] = new Picker(i, sections, pool, seed + i);
            exec.execute(pickers[i-1]);
        }

        // ticker thread with deadlock detection
        Thread ticker = new Thread(() -> {
            int deadlockTicks = 0;
            int deadlockThreshold = 10; // how many ticks of full waiting before declaring deadlock
            try {
                while (Tick.current() < runTicks) {
                    Thread.sleep(tickTimeMs);
                    // Deadlock detection: are all pickers and all stockers waiting?
                    boolean allPickersWaiting = true;
                    for (Picker p : pickers) {
                        if (!p.isWaiting()) {
                            allPickersWaiting = false;
                            break;
                        }
                    }
                    boolean allStockersWaiting = true;
                    for (Stocker s : stockers) {
                        if (!s.isWaiting()) {
                            allStockersWaiting = false;
                            break;
                        }
                    }
                    if (allPickersWaiting && allStockersWaiting) {
                        deadlockTicks++;
                        Logger.log("TICKER", "deadlock_warning", "All pickers and stockers waiting (tick=" + Tick.current() + ")");
                        if (deadlockTicks >= deadlockThreshold) {
                            Logger.log("TICKER", "deadlock", "Simulation deadlocked at tick=" + Tick.current());
                            exec.shutdownNow();
                            break;
                        }
                    } else {
                        deadlockTicks = 0;
                    }
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

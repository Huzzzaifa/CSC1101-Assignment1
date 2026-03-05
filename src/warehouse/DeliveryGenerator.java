package warehouse;

import java.util.Map;
import java.util.EnumMap;
import java.util.Random;

public class DeliveryGenerator implements Runnable {
    private final StagingArea staging;
    private final Random rand;
    private final double probPerTick;
    
    public DeliveryGenerator(StagingArea staging, long seed, double probPerTick) {
        this.staging = staging;
        this.rand = new Random(seed);
        this.probPerTick = probPerTick;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Tick.sleepTicks(1);
            } catch (InterruptedException e) {
                break;
            }
            if (rand.nextDouble() < probPerTick) {
                // create 10 boxes distributed randomly among sections
                Map<SectionType,Integer> boxes = new EnumMap<>(SectionType.class);
                int remaining = 10;
                SectionType[] types = SectionType.values();
                for (int i = 0; i < types.length - 1; i++) {
                    int take = rand.nextInt(remaining + 1);
                    boxes.put(types[i], take);
                    remaining -= take;
                }
                boxes.put(types[types.length - 1], remaining);
                staging.deliver(boxes);
                String details = "";
                for (SectionType t : types) {
                    details += t.name().toLowerCase() + "=" + boxes.getOrDefault(t,0) + " ";
                }
                Logger.log("DEL", "delivery_arrived", details.trim());
            }
        }
    }
}

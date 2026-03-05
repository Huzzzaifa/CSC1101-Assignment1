package warehouse;

import java.util.Map;
import java.util.EnumMap;

public class Stocker implements Runnable {
    private final String id;
    private final StagingArea staging;
    private final Section[] sections;
    private final TrolleyPool pool;

    public Stocker(int num, StagingArea staging, Section[] sections, TrolleyPool pool) {
        this.id = "S" + num;
        this.staging = staging;
        this.sections = sections;
        this.pool = pool;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // take delivery from staging (blocking)
                Map<SectionType,Integer> delivery = staging.takeDelivery();
                // acquire trolley
                Trolley t = pool.acquire();
                Logger.log(id, "acquire_trolley", "trolley_id=" + t.id + " waited_ticks=0");

                // load up to 10 boxes onto trolley
                Map<SectionType,Integer> load = new EnumMap<>(SectionType.class);
                int total = 0;
                for (SectionType s : SectionType.values()) {
                    int num = delivery.getOrDefault(s, 0);
                    if (total + num > 10) {
                        num = 10 - total;
                    }
                    if (num > 0) load.put(s, num);
                    total += num;
                    if (total >= 10) break;
                }
                t.load = total;
                StringBuilder sb = new StringBuilder();
                for (SectionType s : SectionType.values()) {
                    sb.append(s.name().toLowerCase()).append("=")
                      .append(load.getOrDefault(s,0)).append(" ");
                }
                Logger.log(id, "stocker_load", "" + sb.toString().trim() + " total_load=" + total);

                // deliver to sections sequentially
                Section current = null;
                int remaining = total;

                while (remaining > 0) {
                    // pick next section that has some of this load
                    SectionType nextType = null;
                    for (SectionType ttype : SectionType.values()) {
                        if (load.getOrDefault(ttype,0) > 0) {
                            nextType = ttype;
                            break;
                        }
                    }
                    if (nextType == null) break;
                    Section sec = getSection(nextType);
                    // move there from current or staging
                    if (current == null) {
                        Logger.log(id, "move", "from=staging to=" + sec.name.toLowerCase() + " load=" + remaining + " trolley_id=" + t.id);
                        Tick.sleepTicks(10 + remaining);
                    } else {
                        Logger.log(id, "move", "from=" + current.name.toLowerCase() + " to=" + sec.name.toLowerCase() + " load=" + remaining + " trolley_id=" + t.id);
                        Tick.sleepTicks(10 + remaining);
                    }
                    // begin stocking
                    int toStock = load.get(nextType);
                    Logger.log(id, "stock_begin", "section=" + sec.name.toLowerCase() + " amount=" + toStock + " trolley_id=" + t.id);
                    sec.lockSection();
                    int stocked = 0;
                    try {
                        // attempt to add boxes until either amount exhausted or section full
                        while (stocked < toStock) {
                            if (sec.getCount() >= sec.getCapacity()) {
                                // section full
                                break;
                            }
                            sec.addBoxes(1);
                            stocked++;
                            Tick.sleepTicks(1); // time per box
                        }
                    } finally {
                        sec.unlockSection();
                    }
                    remaining -= stocked;
                    load.put(nextType, toStock - stocked);
                    Logger.log(id, "stock_end", "section=" + sec.name.toLowerCase() + " stocked=" + stocked + " remaining_load=" + remaining + " trolley_id=" + t.id);
                    current = sec;
                }

                // return to staging
                Logger.log(id, "move", "from=" + (current==null?"staging":current.name.toLowerCase()) + " to=staging load=" + remaining + " trolley_id=" + t.id);
                Tick.sleepTicks(10 + remaining);
                // release trolley
                Logger.log(id, "release_trolley", "trolley_id=" + t.id + " remaining_load=" + remaining);
                pool.release(t);
                t.load = 0;
                // leftover boxes remain in staging area implicitly (ignored for now)
            }
        } catch (InterruptedException e) {
            // exit
        }
    }

    private Section getSection(SectionType type) {
        for (Section s : sections) {
            if (s.name.equalsIgnoreCase(type.name().toLowerCase())) return s;
        }
        return null;
    }
}

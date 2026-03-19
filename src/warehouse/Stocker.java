package warehouse;

import java.util.Map;
import java.util.EnumMap;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Stocker implements Runnable {
    private final String id;
    private final StagingArea staging;
    private final Section[] sections;
    private final TrolleyPool pool;
    private final Random rand;
    private final int breakMin;
    private final int breakMax;
    private final int breakDuration;

    private long nextBreakTick;

    private volatile boolean waiting = false;

    public boolean isWaiting() {
        return waiting;
    }

    public Stocker(int num, StagingArea staging, Section[] sections, TrolleyPool pool, int breakMin, int breakMax, int breakDuration) {
        this.id = "S" + num;
        this.staging = staging;
        this.sections = sections;
        this.pool = pool;
        this.rand = new Random();
        this.breakMin = breakMin;
        this.breakMax = breakMax;
        this.breakDuration = breakDuration;
        scheduleNextBreak();
    }

    private void scheduleNextBreak() {
        long now = Tick.current();
        int interval = breakMin + rand.nextInt(breakMax - breakMin + 1);
        nextBreakTick = now + interval;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Check for break
                long now = Tick.current();
                if (now >= nextBreakTick) {
                    Logger.log(id, "break_start", "duration_ticks=" + breakDuration);
                    Tick.sleepTicks(breakDuration);
                    Logger.log(id, "break_end", "duration_ticks=" + breakDuration);
                    scheduleNextBreak();
                    continue;
                }
                // take delivery from staging (blocking)
                waiting = true;
                Logger.log(id, "wait_delivery", "Waiting for delivery at staging area");
                Map<SectionType,Integer> delivery = staging.takeDelivery();
                waiting = false;
                // acquire trolley
                waiting = true;
                Logger.log(id, "wait_trolley", "Waiting for trolley");
                Trolley t = pool.acquire();
                waiting = false;
                Logger.log(id, "acquire_trolley", "trolley_id=" + t.id + " waited_ticks=0");

                // load up to 10 boxes onto trolley; prioritize sections with most pickers waiting or empty
                Map<SectionType,Integer> load = new EnumMap<>(SectionType.class);
                int total = 0;
                List<SectionType> types = new ArrayList<>(List.of(SectionType.values()));
                // Sort by: (1) number of waiting pickers (desc), (2) if section is empty (desc)
                types.sort((a, b) -> {
                    Section sa = getSection(a);
                    Section sb = getSection(b);
                    int cmp = Integer.compare(sb.getWaitingPickers(), sa.getWaitingPickers());
                    if (cmp != 0) return cmp;
                    // Prefer empty sections
                    cmp = Integer.compare((sa.getCount() == 0 ? 1 : 0), (sb.getCount() == 0 ? 1 : 0));
                    return -cmp;
                });
                for (SectionType s : types) {
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
                    // Always log stock_end with stocked and remaining_load
                    Logger.log(id, "stock_end", "section=" + sec.name.toLowerCase() + " stocked=" + stocked + " remaining_load=" + (remaining) + " trolley_id=" + t.id);
                    if (stocked == 0) {
                        // section was full and we couldn't add anything; stop trying to
                        // deliver the rest of this trolley so we return to staging and
                        // pick up a new delivery instead of looping forever.
                        break;
                    }
                    remaining -= stocked;
                    load.put(nextType, toStock - stocked);
                    current = sec;
                }

                // return to staging
                Logger.log(id, "move", "from=" + (current==null?"staging":current.name.toLowerCase()) + " to=staging load=" + remaining + " trolley_id=" + t.id);
                Tick.sleepTicks(10 + remaining);
                // Only release trolley if remaining_load == 0
                if (remaining > 0) {
                    Logger.log(id, "trolley_not_released", "trolley_id=" + t.id + " remaining_load=" + remaining + " (trolley held, not released)");
                } else {
                    Logger.log(id, "release_trolley", "trolley_id=" + t.id + " remaining_load=0");
                    pool.release(t);
                    t.load = 0;
                }
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

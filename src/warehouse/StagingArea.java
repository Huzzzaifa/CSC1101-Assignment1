package warehouse;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.HashMap;

public class StagingArea {
    private final BlockingQueue<Map<SectionType,Integer>> queue = new LinkedBlockingQueue<>();

    public void deliver(Map<SectionType,Integer> boxes) {
        queue.offer(boxes);
    }

    public Map<SectionType,Integer> takeDelivery() throws InterruptedException {
        return queue.take();
    }

    public boolean hasDelivery() {
        return !queue.isEmpty();
    }
}

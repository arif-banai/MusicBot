package com.jagrosh.jmusicbot.unit.queue;

import com.jagrosh.jmusicbot.queue.LinearQueue;
import com.jagrosh.jmusicbot.queue.Queueable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LinearQueueTest {

    @Test
    public void testAddAndPull() {
        LinearQueue<Q> queue = new LinearQueue<>(null);
        Q q1 = new Q(1);
        Q q2 = new Q(2);

        assertEquals(0, queue.add(q1));
        assertEquals(1, queue.add(q2));
        assertEquals(2, queue.size());

        assertEquals(q1, queue.pull());
        assertEquals(1, queue.size());
        assertEquals(q2, queue.pull());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testRewind() {
        LinearQueue<Q> queue = new LinearQueue<>(null);
        queue.setMaxHistorySize(10);
        Q q1 = new Q(1);
        Q q2 = new Q(2);
        
        queue.addToHistory(q1);
        
        Q rewinded = queue.rewind(q2);
        assertEquals(q1, rewinded);
        assertEquals(1, queue.size());
        assertEquals(q2, queue.get(0));
    }

    @Test
    public void testMoveItem() {
        LinearQueue<Q> queue = new LinearQueue<>(null);
        Q q1 = new Q(1);
        Q q2 = new Q(2);
        Q q3 = new Q(3);

        queue.add(q1);
        queue.add(q2);
        queue.add(q3);

        queue.moveItem(0, 2); // Move q1 to the end
        assertEquals(q2, queue.get(0));
        assertEquals(q3, queue.get(1));
        assertEquals(q1, queue.get(2));
    }

    @Test
    public void testRemoveAll() {
        LinearQueue<Q> queue = new LinearQueue<>(null);
        queue.add(new Q(1));
        queue.add(new Q(2));
        queue.add(new Q(1));
        queue.add(new Q(3));

        int removed = queue.removeAll(1);
        assertEquals(2, removed);
        assertEquals(2, queue.size());
        assertEquals(2L, queue.get(0).getIdentifier());
        assertEquals(3L, queue.get(1).getIdentifier());
    }

    private static class Q implements Queueable {
        private final long id;
        Q(long id) { this.id = id; }
        @Override public long getIdentifier() { return id; }
    }
}

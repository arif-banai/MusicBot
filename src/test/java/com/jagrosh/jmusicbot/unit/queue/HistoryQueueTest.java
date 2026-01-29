package com.jagrosh.jmusicbot.unit.queue;

import com.jagrosh.jmusicbot.queue.HistoryQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class HistoryQueueTest {

    @Test
    public void testAddAndSize() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        queue.setMaxSize(3);
        
        queue.add("one");
        assertEquals(1, queue.size());
        assertEquals("one", queue.get(0));

        queue.add("two");
        assertEquals(2, queue.size());
        assertEquals("two", queue.get(0));
        assertEquals("one", queue.get(1));
    }

    @Test
    public void testMaxSize() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        queue.setMaxSize(2);

        queue.add("one");
        queue.add("two");
        queue.add("three");

        assertEquals(2, queue.size());
        assertEquals("three", queue.get(0));
        assertEquals("two", queue.get(1));
        
        // Ensure "one" was removed (it was the oldest)
        List<String> list = queue.getList();
        assertFalse(list.contains("one"));
    }

    @Test
    public void testRemoveFirst() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        queue.setMaxSize(10);
        queue.add("one");
        queue.add("two");

        assertEquals("two", queue.removeFirst());
        assertEquals(1, queue.size());
        assertEquals("one", queue.get(0));

        assertEquals("one", queue.removeFirst());
        assertTrue(queue.isEmpty());
        assertNull(queue.removeFirst());
    }

    @Test
    public void testSetMaxSizeShrink() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        queue.setMaxSize(5);
        queue.add("1");
        queue.add("2");
        queue.add("3");
        queue.add("4");
        queue.add("5");

        queue.setMaxSize(2);
        assertEquals(2, queue.size());
        assertEquals("5", queue.get(0));
        assertEquals("4", queue.get(1));
    }

    @Test
    public void testSetNegativeMaxSize() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        assertThrows(IllegalArgumentException.class, () -> queue.setMaxSize(-1));
    }

    @Test
    public void testClear() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        queue.setMaxSize(10);
        queue.add("one");
        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    public void testAddNull() {
        HistoryQueue<String> queue = new HistoryQueue<>();
        queue.add(null);
        assertEquals(0, queue.size());
    }
}

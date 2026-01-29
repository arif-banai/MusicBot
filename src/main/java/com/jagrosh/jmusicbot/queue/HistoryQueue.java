package com.jagrosh.jmusicbot.queue;

import java.util.LinkedList;
import java.util.List;

/**
 * A bounded LIFO (Last In, First Out) queue for tracking playback history.
 * Most recently played tracks are stored at the front (index 0) and are the first to be removed.
 * 
 * @author Arif Banai
 * @param <T> The type of items to store in history
 */
public class HistoryQueue<T> {
    private final LinkedList<T> history;
    private int maxSize;

    /**
     * Creates a new HistoryQueue.
     * The max size must be set via setMaxSize() before use.
     */
    public HistoryQueue() {
        this.history = new LinkedList<>();
    }

    /**
     * Adds an item to the history. The item is added at the front (most recent).
     * If the history is at max size, the oldest item is removed.
     * 
     * @param item The item to add to history
     */
    public void add(T item) {
        if (item == null) {
            return;
        }
        history.addFirst(item);
        // Remove oldest items if we exceed max size
        while (history.size() > maxSize) {
            history.removeLast();
        }
    }

    /**
     * Removes and returns the most recently added item (from the front).
     * This is used for rewinding to previous tracks.
     * 
     * @return The most recently added item, or null if history is empty
     */
    public T removeFirst() {
        if (history.isEmpty()) {
            return null;
        }
        return history.removeFirst();
    }

    /**
     * Sets the maximum number of items to keep in history.
     * If the current history size exceeds the new max size, oldest items are removed.
     * 
     * @param size The maximum number of items to keep (must be >= 0)
     */
    public void setMaxSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Max size cannot be negative");
        }
        this.maxSize = size;
        // Remove oldest items if current size exceeds new max
        while (history.size() > maxSize) {
            history.removeLast();
        }
    }

    /**
     * Gets the maximum number of items this history can hold.
     * 
     * @return The maximum size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Clears all items from the history.
     */
    public void clear() {
        history.clear();
    }

    /**
     * Gets the current number of items in history.
     * 
     * @return The current size
     */
    public int size() {
        return history.size();
    }

    /**
     * Checks if the history is empty.
     * 
     * @return true if history is empty, false otherwise
     */
    public boolean isEmpty() {
        return history.isEmpty();
    }

    /**
     * Gets an unmodifiable view of the history list.
     * Most recent items are at index 0, oldest at the end.
     * 
     * @return An unmodifiable list of history items
     */
    public List<T> getList() {
        return List.copyOf(history);
    }

    /**
     * Gets the item at the specified index.
     * Index 0 is the most recent item.
     * 
     * @param index The index (0 = most recent)
     * @return The item at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public T get(int index) {
        return history.get(index);
    }
}

/*
 * Copyright 2022 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.queue;

import com.jagrosh.jmusicbot.audio.QueuedTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 *
 * @author Wolfgang Schwendtbauer
 * @param <T>
 */
public abstract class AbstractQueue<T extends Queueable>
{
    /**
     * Creates a new queue, optionally copying state from an existing queue.
     * 
     * @param queue The previous queue to copy state from, or null for initial creation
     * @param maxHistorySize The maximum history size (only used when queue is null)
     */
    protected AbstractQueue(AbstractQueue<T> queue, int maxHistorySize)
    {
        // Use ArrayList for O(1) random access (display, shuffle, etc.)
        // Copy the list when switching queue types to avoid shared mutable state
        this.list = queue != null ? new ArrayList<>(queue.getList()) : new ArrayList<>();
        // Reuse history when switching queue types; always apply configured max size so transitions
        // (including maxHistorySize=0 disabled mode) take effect immediately.
        this.history = queue != null ? queue.getHistory() : new PlaybackHistory<>(maxHistorySize, qt -> qt instanceof QueuedTrack ? ((QueuedTrack) qt).getTrack().getIdentifier() : qt.getIdentifier());
        this.history.setMaxSize(maxHistorySize);
    }

    protected final List<T> list;
    protected final PlaybackHistory<T> history;

    public abstract int add(T item);

    public void addAt(int index, T item)
    {
        if(index >= list.size())
            list.add(item);
        else
            list.add(index, item);
    }

    public int size() {
        return list.size();
    }

    public T pull() {
        if (list.isEmpty())
            return null;
        return list.remove(0);
    }

    public void addToHistory(T item)
    {
        history.add(item);
    }

    public void setMaxHistorySize(int size)
    {
        history.setMaxSize(size);
    }

    /**
     * Clears playback history entries.
     */
    public void clearHistory()
    {
        history.clear();
    }

    public T removeLastPlayed()
    {
        return history.removeFirst();
    }

    /**
     * Removes the history entry at the given index (0 = most recent).
     * Used when a user replays a track from history so it is not retained.
     *
     * @param index The history index to remove
     * @return The removed item, or null if index is out of range
     */
    public T removeFromHistoryAt(int index)
    {
        return history.removeAt(index);
    }

    /**
     * Removes the first history entry that matches the predicate.
     * Used when a track starts playing to avoid duplicate entries in history.
     *
     * @param predicate The condition to match (e.g. same track identifier)
     * @return true if an entry was removed, false otherwise
     */
    public boolean removeFromHistoryFirstMatch(Predicate<T> predicate)
    {
        return history.removeFirstMatching(predicate);
    }

    /**
     * Rewinds the queue by taking the last played item from history
     * and optionally pushing the current item back to the front of the queue.
     * @param current The currently playing item to push back to the queue
     * @return The previous item to play, or null if history is empty
     */
    public T rewind(T current)
    {
        T prev = history.removeFirst();
        if (prev != null && current != null)
        {
            list.add(0, current);
        }
        return prev;
    }

    public boolean isEmpty()
    {
        return list.isEmpty();
    }

    public List<T> getList()
    {
        return list;
    }

    public PlaybackHistory<T> getHistory()
    {
        return history;
    }

    public T get(int index) {
        return list.get(index);
    }

    /**
     * Replaces every queued item with {@code op}'s result, in queue order, keeping positions.
     */
    public void replaceAll(UnaryOperator<T> op)
    {
        list.replaceAll(op);
    }

    public T remove(int index)
    {
        return list.remove(index);
    }

    public int removeAll(long identifier)
    {
        int count = 0;
        for(int i=list.size()-1; i>=0; i--)
        {
            if(list.get(i).getIdentifier()==identifier)
            {
                list.remove(i);
                count++;
            }
        }
        return count;
    }

    public void clear()
    {
        list.clear();
    }

    public void clearAll()
    {
        list.clear();
        history.clear();
    }

    public int shuffle(long identifier)
    {
        List<Integer> iset = new ArrayList<>();
        for(int i=0; i<list.size(); i++)
        {
            if(identifier == 0 || list.get(i).getIdentifier()==identifier)
                iset.add(i);
        }
        for(int j=0; j<iset.size(); j++)
        {
            int first = iset.get(j);
            int second = iset.get((int)(Math.random()*iset.size()));
            T temp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, temp);
        }
        return iset.size();
    }

    public void skip(int number)
    {
        if (number > 0) {
            list.subList(0, number).clear();
        }
    }

    /**
     * Move an item to a different position in the list
     * @param from The position of the item
     * @param to The new position of the item
     * @return the moved item
     */
    public T moveItem(int from, int to)
    {
        T item = list.remove(from);
        list.add(to, item);
        return item;
    }
}

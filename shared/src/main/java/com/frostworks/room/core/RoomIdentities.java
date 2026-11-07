package com.frostworks.room.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Room numbers that survive a new search. Pure Java, no Minecraft class.
 * <p>
 * A room is searched again after a change (wall placed or broken): the old room is <em>retired</em> with its cells and
 * the seeds (vents) that will search it again; each new room found from those seeds is matched to the retired rooms it
 * overlaps:
 * <ul>
 * <li>a retired room gives its number to the new room that overlaps it the most (a split: the biggest piece keeps the
 * number, the other pieces get new ones);</li>
 * <li>a new room overlapping several retired rooms (a merge) keeps the number of the one it overlaps the most;</li>
 * <li>a new room that overlaps no retired room is a new room: next free number.</li>
 * </ul>
 * The pieces of a split are found one search at a time, so the numbers of a group are worked out again each time one
 * of its pieces is found, until every seed of the retired rooms has been searched.
 *
 * @param <R> the caller's room object
 */
public final class RoomIdentities<R> {

    private final class Retired {
        final int id;
        final LongOpenHashSet cells;
        final LongOpenHashSet pendingSeeds;
        final long since;
        /** New rooms that overlap it, with the size of the overlap. */
        final Map<R, Integer> successors = new LinkedHashMap<>();

        Retired(int id, LongSet cells, LongSet seeds, long since) {
            this.id = id;
            this.cells = new LongOpenHashSet(cells);
            this.pendingSeeds = new LongOpenHashSet(seeds);
            this.since = since;
        }
    }

    private final List<Retired> retired = new ArrayList<>();
    /** Current number of every new room that still belongs to an open group. */
    private final Map<R, Integer> ids = new LinkedHashMap<>();
    private int nextId = 1;

    /** A number already in use (a room read back from the save): never given to a new room. */
    public void reserve(int id) {
        nextId = Math.max(nextId, id + 1);
    }

    /** A new number, never given before. */
    public int freshId() {
        return nextId++;
    }

    /** A room is about to be searched again: remember its number, its cells and the seeds that will search it. */
    public void retire(int id, LongSet cells, LongSet seeds, long now) {
        if (seeds.isEmpty())
            return; // nothing will ever search it again: its number is gone
        // The same number already waiting (a room searched again before its last search settled, while it is being
        // rebuilt): one entry, or two new rooms could both inherit that number
        for (Retired r : retired)
            if (r.id == id) {
                r.cells.addAll(cells);
                r.pendingSeeds.addAll(seeds);
                return;
            }
        retired.add(new Retired(id, cells, seeds, now));
    }

    /** A seed was handled without making a new room (it joined a known room, blows into a wall, was removed...). */
    public void seedDone(long seed) {
        for (Retired r : retired)
            r.pendingSeeds.remove(seed);
        dropFinished();
    }

    /**
     * A search from {@code seed} found a new room. Returns the numbers of the new rooms of its group: this one and
     * the other pieces found before it (a later piece can take a number given to an earlier, smaller piece).
     */
    public Map<R, Integer> found(R room, LongSet cells, long seed) {
        List<Retired> group = new ArrayList<>();
        for (Retired r : retired) {
            int overlap = overlap(cells, r.cells);
            if (overlap > 0) {
                r.successors.put(room, overlap);
                group.add(r);
            }
        }
        for (Retired r : retired)
            r.pendingSeeds.remove(seed);
        Map<R, Integer> result = new LinkedHashMap<>();
        if (group.isEmpty()) {
            result.put(room, freshId());
            dropFinished();
            return result;
        }
        // The whole group: retired rooms that share a new room with it
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Retired r : retired)
                if (!group.contains(r) && sharesSuccessor(r, group)) {
                    group.add(r);
                    grew = true;
                }
        }
        List<R> news = new ArrayList<>();
        for (Retired r : group)
            for (R s : r.successors.keySet())
                if (!news.contains(s))
                    news.add(s);
        assign(group, news, result);
        ids.putAll(result);
        dropFinished();
        return result;
    }

    private boolean sharesSuccessor(Retired r, List<Retired> group) {
        for (Retired g : group)
            for (R s : r.successors.keySet())
                if (g.successors.containsKey(s))
                    return true;
        return false;
    }

    private void assign(List<Retired> olds, List<R> news, Map<R, Integer> out) {
        // Each retired room goes to the new room that overlaps it the most (first found on a tie)
        Map<Retired, R> winner = new LinkedHashMap<>();
        for (Retired old : olds) {
            R best = null;
            int bestOverlap = 0;
            for (R s : news) {
                Integer o = old.successors.get(s);
                if (o != null && o > bestOverlap) {
                    best = s;
                    bestOverlap = o;
                }
            }
            if (best != null)
                winner.put(old, best);
        }
        IntOpenHashSet inherited = new IntOpenHashSet();
        IntOpenHashSet retiredIds = new IntOpenHashSet();
        for (Retired old : olds)
            retiredIds.add(old.id);
        for (R s : news) {
            Retired chosen = null;
            for (Map.Entry<Retired, R> w : winner.entrySet())
                if (w.getValue() == s) {
                    Retired old = w.getKey();
                    if (chosen == null || old.successors.get(s) > chosen.successors.get(s)
                            || old.successors.get(s).equals(chosen.successors.get(s)) && old.id < chosen.id)
                        chosen = old;
                }
            if (chosen != null) {
                out.put(s, chosen.id);
                inherited.add(chosen.id);
            }
        }
        for (R s : news) {
            if (out.containsKey(s))
                continue;
            Integer previous = ids.get(s);
            out.put(s, previous != null && !inherited.contains(previous) && !retiredIds.contains(previous.intValue())
                    ? previous : freshId());
        }
    }

    private void dropFinished() {
        for (Iterator<Retired> it = retired.iterator(); it.hasNext(); ) {
            Retired r = it.next();
            if (r.pendingSeeds.isEmpty()) {
                it.remove();
                for (R s : r.successors.keySet())
                    if (!stillOpen(s))
                        ids.remove(s);
            }
        }
    }

    private boolean stillOpen(R room) {
        for (Retired r : retired)
            if (r.successors.containsKey(room))
                return true;
        return false;
    }

    /** Forgets groups older than {@code maxAge} (a seed that never came back, e.g. in a chunk that unloaded). */
    public void expire(long now, long maxAge) {
        for (Retired r : retired)
            if (now - r.since > maxAge)
                r.pendingSeeds.clear();
        dropFinished();
    }

    /** A new room was dropped before its group settled: forget it. */
    public void forget(R room) {
        for (Retired r : retired)
            r.successors.remove(room);
        ids.remove(room);
    }

    /** Retired rooms still waiting for some of their seeds (tests, status). */
    public int openGroups() {
        return retired.size();
    }

    static int overlap(LongSet a, LongSet b) {
        LongSet small = a.size() <= b.size() ? a : b;
        LongSet big = small == a ? b : a;
        int n = 0;
        for (LongIterator it = small.iterator(); it.hasNext(); )
            if (big.contains(it.nextLong()))
                n++;
        return n;
    }
}

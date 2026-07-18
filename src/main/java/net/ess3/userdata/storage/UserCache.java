package net.ess3.userdata.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory offline player name/uuid cache — real cover behavior.
 */
public final class UserCache {

    private final Map<UUID, Entry> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private volatile int maxEntries;
    private volatile long ttlMs;

    public UserCache(int maxEntries, long ttlMs) {
        this.maxEntries = Math.max(16, maxEntries);
        this.ttlMs = Math.max(60_000L, ttlMs);
    }

    public void reconfigure(int maxEntries, long ttlMs) {
        this.maxEntries = Math.max(16, maxEntries);
        this.ttlMs = Math.max(60_000L, ttlMs);
        evictExpired();
    }

    public void touch(UUID id, String name) {
        if (id == null || name == null || name.isEmpty()) return;
        long now = System.currentTimeMillis();
        byId.put(id, new Entry(name, now));
        byName.put(name.toLowerCase(), id);
        if (byId.size() > maxEntries) {
            evictOldest();
        }
    }

    public String nameOf(UUID id) {
        Entry e = byId.get(id);
        if (e == null) return null;
        if (System.currentTimeMillis() - e.at > ttlMs) {
            byId.remove(id);
            byName.remove(e.name.toLowerCase());
            return null;
        }
        return e.name;
    }

    public UUID idOf(String name) {
        if (name == null) return null;
        UUID id = byName.get(name.toLowerCase());
        if (id == null) return null;
        if (nameOf(id) == null) return null;
        return id;
    }

    public int size() {
        evictExpired();
        return byId.size();
    }

    public void clear() {
        byId.clear();
        byName.clear();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        byId.entrySet().removeIf(e -> {
            if (now - e.getValue().at > ttlMs) {
                byName.remove(e.getValue().name.toLowerCase());
                return true;
            }
            return false;
        });
    }

    private void evictOldest() {
        UUID oldest = null;
        long t = Long.MAX_VALUE;
        for (Map.Entry<UUID, Entry> e : byId.entrySet()) {
            if (e.getValue().at < t) {
                t = e.getValue().at;
                oldest = e.getKey();
            }
        }
        if (oldest != null) {
            Entry removed = byId.remove(oldest);
            if (removed != null) byName.remove(removed.name.toLowerCase());
        }
    }

    private static final class Entry {
        final String name;
        final long at;

        Entry(String name, long at) {
            this.name = name;
            this.at = at;
        }
    }
}

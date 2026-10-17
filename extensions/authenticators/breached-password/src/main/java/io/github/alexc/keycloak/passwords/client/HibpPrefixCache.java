package io.github.alexc.keycloak.passwords.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-safe bounded LRU cache with TTL for HIBP k-Anonymity 5-character prefixes.
 */
public class HibpPrefixCache {

    private static volatile HibpPrefixCache SHARED_INSTANCE;

    private final int maxEntries;
    private final long ttlMillis;
    private final Map<String, CacheEntry> cache;

    private record CacheEntry(Map<String, Integer> suffixes, long expiresAtMillis) {
        boolean isExpired(long currentTimeMillis) {
            return currentTimeMillis > expiresAtMillis;
        }
    }

    /**
     * Retrieves or lazily creates a shared singleton instance of the prefix cache.
     *
     * @param maxEntries maximum number of prefixes stored
     * @param ttlMinutes time to live for each entry in minutes
     * @return shared HibpPrefixCache instance
     */
    public static synchronized HibpPrefixCache getOrCreateShared(int maxEntries, int ttlMinutes) {
        if (SHARED_INSTANCE == null) {
            SHARED_INSTANCE = new HibpPrefixCache(maxEntries, ttlMinutes);
        }
        return SHARED_INSTANCE;
    }

    /**
     * Clears and resets the shared singleton cache instance.
     */
    public static synchronized void resetShared() {
        if (SHARED_INSTANCE != null) {
            SHARED_INSTANCE.clear();
            SHARED_INSTANCE = null;
        }
    }

    /**
     * Initializes the bounded LRU cache with specified maximum capacity and TTL in minutes.
     *
     * @param maxEntries maximum number of prefixes stored
     * @param ttlMinutes time to live for each entry in minutes
     */
    public HibpPrefixCache(int maxEntries, int ttlMinutes) {
        this.maxEntries = Math.max(10, maxEntries);
        this.ttlMillis = Math.max(1, ttlMinutes) * 60L * 1000L;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return super.size() > HibpPrefixCache.this.maxEntries;
            }
        };
    }

    /**
     * Retrieves the cached suffix map for a prefix, or null if missing or expired.
     *
     * @param prefix 5-character SHA-1 prefix
     * @return map of suffixes to breach counts, or null if not cached/expired
     */
    public synchronized Map<String, Integer> get(String prefix) {
        if (prefix == null) {
            return null;
        }
        String normalizedPrefix = prefix.toUpperCase(Locale.ROOT);
        CacheEntry entry = cache.get(normalizedPrefix);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(System.currentTimeMillis())) {
            cache.remove(normalizedPrefix);
            return null;
        }
        return entry.suffixes();
    }

    /**
     * Caches the parsed suffix map for the given prefix.
     *
     * @param prefix   5-character SHA-1 prefix
     * @param suffixes parsed map of suffixes to breach counts
     */
    public synchronized void put(String prefix, Map<String, Integer> suffixes) {
        if (prefix == null || suffixes == null) {
            return;
        }
        String normalizedPrefix = prefix.toUpperCase(Locale.ROOT);
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        cache.put(normalizedPrefix, new CacheEntry(suffixes, expiresAt));
    }

    /**
     * Clears all cached entries.
     */
    public synchronized void clear() {
        cache.clear();
    }

    /**
     * Returns the current number of cached entries.
     *
     * @return cache size
     */
    public synchronized int size() {
        return cache.size();
    }
}

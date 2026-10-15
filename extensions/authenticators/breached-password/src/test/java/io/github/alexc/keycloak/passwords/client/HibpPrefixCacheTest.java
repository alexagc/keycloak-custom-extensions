package io.github.alexc.keycloak.passwords.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HibpPrefixCacheTest {

    @Test
    @DisplayName("Basic put, get and case normalization in prefix cache")
    void testBasicPutAndGet() {
        HibpPrefixCache cache = new HibpPrefixCache(100, 60);

        Map<String, Integer> entries = Map.of("1E4C9B93F3F0682250B6CF8331B7EE68FD8", 42);
        cache.put("5baa6", entries); // lowercase put

        Map<String, Integer> result = cache.get("5BAA6"); // uppercase get
        assertNotNull(result);
        assertEquals(42, result.get("1E4C9B93F3F0682250B6CF8331B7EE68FD8"));

        assertNull(cache.get("00000"));
        assertNull(cache.get(null));
    }

    @Test
    @DisplayName("Cache capacity evicts eldest entry when maxEntries is exceeded")
    void testCapacityEviction() {
        int maxEntries = 10;
        HibpPrefixCache cache = new HibpPrefixCache(maxEntries, 60);

        for (int i = 0; i < 15; i++) {
            String prefix = String.format("%05X", i);
            cache.put(prefix, Map.of("SUFFIX", i));
        }

        assertEquals(maxEntries, cache.size());
        // Earliest entries (0, 1, 2, 3, 4) should have been evicted
        assertNull(cache.get(String.format("%05X", 0)));
        assertNotNull(cache.get(String.format("%05X", 14)));
    }

    @Test
    @DisplayName("Null handling and clear operations")
    void testNullAndClear() {
        HibpPrefixCache cache = new HibpPrefixCache(50, 10);
        cache.put(null, Map.of("A", 1));
        cache.put("12345", null);
        assertEquals(0, cache.size());

        cache.put("AAAAA", Map.of("A", 1));
        cache.put("BBBBB", Map.of("B", 2));
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("AAAAA"));
    }
}

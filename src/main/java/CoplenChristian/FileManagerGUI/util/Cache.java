package CoplenChristian.FileManagerGUI.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small LRU cache with TTL and a shallow directory signature helper.
 * Stores directory sizes keyed by Path along with a signature to validate freshness.
 */
public final class Cache {

    /** Shallow signature of a directory (name/mtime of children + dir mtime). */
    public static final class DirSignature {
        public final long hash;
        public DirSignature(long hash) { this.hash = hash; }
    }

    /** Cache value for a directory size. */
    public static final class CacheEntry {
        public final long sizeBytes;
        public final long cachedAtMillis;
        public final DirSignature sig;
        public CacheEntry(long sizeBytes, long cachedAtMillis, DirSignature sig) {
            this.sizeBytes = sizeBytes;
            this.cachedAtMillis = cachedAtMillis;
            this.sig = sig;
        }
    }

    private final long ttlMillis;
    private final Map<Path, CacheEntry> lru;

    /**
     * @param maxEntries maximum number of entries to retain (LRU eviction)
     * @param ttlMillis  time-to-live for entries in milliseconds
     */
    public Cache(int maxEntries, long ttlMillis) {
        this.ttlMillis = ttlMillis;
        this.lru = Collections.synchronizedMap(
            new LinkedHashMap<Path, CacheEntry>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Path, CacheEntry> eldest) {
                    return size() > maxEntries;
                }
            }
        );
    }

    /** Put/replace an entry. */
    public void put(Path key, long sizeBytes, DirSignature sig) {
        lru.put(normalize(key), new CacheEntry(sizeBytes, System.currentTimeMillis(), sig));
    }

    /** Get an entry (may be stale — check with {@link #isValid(CacheEntry, DirSignature)}). */
    public CacheEntry get(Path key) {
        return lru.get(normalize(key));
    }

    /** Remove a single key. */
    public void invalidate(Path key) {
        lru.remove(normalize(key));
    }

    /** Clear all entries. */
    public void clear() {
        lru.clear();
    }

    /** True if the entry is within TTL and its signature matches the current one. */
    public boolean isValid(CacheEntry e, DirSignature currentSig) {
        if (e == null || currentSig == null || e.sig == null) return false;
        if (e.sig.hash != currentSig.hash) return false;
        long age = System.currentTimeMillis() - e.cachedAtMillis;
        return age <= ttlMillis;
    }

    // ---------- Signature helpers ----------

    /** Compute a shallow signature for {@code dir}. */
    public static DirSignature computeShallowSignature(Path dir) {
        long h = 1469598103934665603L; // FNV-1a 64-bit offset basis
        h = fnv1a64(h, safeLM(dir));
        try (var stream = Files.list(dir)) {
            for (Path child : (Iterable<Path>) stream::iterator) {
                h = fnv1a64(h, strHash(child.getFileName().toString()));
                h = fnv1a64(h, safeLM(child));
            }
        } catch (IOException ignored) {}
        return new DirSignature(h);
    }

    private static long safeLM(Path p) {
        try { return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (IOException e) { return 0L; }
    }
    private static long strHash(String s) { return (s == null) ? 0L : s.hashCode(); }
    private static long fnv1a64(long h, long v) { h ^= (v ^ (v >>> 32)); return h * 0x100000001b3L; }
    private static Path normalize(Path p) { return p.toAbsolutePath().normalize(); }

    private Cache() { this(1, 1); } // unused
}

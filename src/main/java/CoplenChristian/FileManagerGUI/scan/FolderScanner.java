package CoplenChristian.FileManagerGUI.scan;

import CoplenChristian.FileManagerGUI.util.AppConfig;
import CoplenChristian.FileManagerGUI.util.Cache;
import CoplenChristian.FileManagerGUI.util.Cache.CacheEntry;
import CoplenChristian.FileManagerGUI.util.Cache.DirSignature;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Core scanning, sizing, and delete helpers (no Swing).
 */
public class FolderScanner implements AutoCloseable {

    /** Data model for each item (file or folder). */
    public static final class Item {
        public final String name;
        public final Path path;
        public final boolean isDirectory;
        public final long sizeBytes;
        public final boolean fromCache;

        public Item(String name, Path path, boolean isDirectory, long sizeBytes, boolean fromCache) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
            this.sizeBytes = sizeBytes;
            this.fromCache = fromCache;
        }
    }

    private static final class SizeResult {
        final long bytes;
        final boolean fromCache;
        SizeResult(long b, boolean c) { bytes = b; fromCache = c; }
    }

    // ------------------------------------------------------------------------

    private final Cache cache;
    private final ExecutorService exec;

    public FolderScanner() {
        this.cache = new Cache(
                AppConfig.cacheMaxEntries(),
                AppConfig.cacheTtlMillis()
        );
        this.exec = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors())
        );
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownNow));
    }

    public void clearCache() { cache.clear(); }
    public void invalidate(Path p) { cache.invalidate(p); }

    // ------------------------------------------------------------------------
    // Public operations
    // ------------------------------------------------------------------------

    /** List files/folders inside dir, with sizes, sorted by size DESC. */
    public List<Item> listFolderContents(Path dir, AtomicBoolean cancel) throws IOException {
        File[] arr = dir.toFile().listFiles();
        if (arr == null) return List.of();

        List<CompletableFuture<Item>> futures = new ArrayList<>(arr.length);
        Semaphore sem = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

        for (File f : arr) {
            final Path p = f.toPath();
            if (f.isDirectory()) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        sem.acquire();
                        if (cancel.get()) return new Item(f.getName(), p, true, 0, false);
                        SizeResult r = dirSizeWithCache(p, cancel);
                        return new Item(f.getName(), p, true, r.bytes, r.fromCache);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new Item(f.getName(), p, true, 0, false);
                    } finally {
                        sem.release();
                    }
                }, exec));
            } else {
                futures.add(CompletableFuture.supplyAsync(() ->
                        new Item(f.getName(), p, false, f.length(), true), exec));
            }
        }
        List<Item> out = joinItems(cancel, futures);
        out.sort(Comparator.comparingLong((Item i) -> i.sizeBytes).reversed());
        return out;
    }

    /** List only immediate subfolders under parent with their sizes. */
    public List<Item> listFoldersAndSizes(Path parent, AtomicBoolean cancel) throws IOException {
        List<Path> subdirs = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(parent)) {
            for (Path p : s)
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    subdirs.add(p);
        }
        List<CompletableFuture<Item>> futures = new ArrayList<>(subdirs.size());
        Semaphore sem = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

        for (Path d : subdirs) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                String name = String.valueOf(d.getFileName());
                try {
                    sem.acquire();
                    if (cancel.get()) return new Item(name, d, true, 0, false);
                    SizeResult r = dirSizeWithCache(d, cancel);
                    return new Item(name, d, true, r.bytes, r.fromCache);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Item(name, d, true, 0, false);
                } finally {
                    sem.release();
                }
            }, exec));
        }
        List<Item> out = joinItems(cancel, futures);
        out.sort(Comparator.comparingLong((Item i) -> i.sizeBytes).reversed());
        return out;
    }

    /** Delete path — tries move-to-trash first; falls back to permanent delete if allowed. */
    public boolean delete(Path p, boolean allowPermanent) {
        try {
            boolean supportTrash = Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
            if (supportTrash && Desktop.getDesktop().moveToTrash(p.toFile())) return true;
            if (!allowPermanent) return false;
            permanentDelete(p);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            invalidate(p);
            Path parent = p.getParent();
            if (parent != null) invalidate(parent);
        }
    }
    
    public java.util.List<Item> topKLargestFoldersInDrive(java.nio.file.Path root, int k, java.util.concurrent.atomic.AtomicBoolean cancel)
            throws java.io.IOException {
        return CoplenChristian.FileManagerGUI.scan.TopKFinder.findTopK(root, k, cancel);
    }

    // ------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------

    private SizeResult dirSizeWithCache(Path dir, AtomicBoolean cancel) {
        Path abs = dir.toAbsolutePath().normalize();
        DirSignature sig = Cache.computeShallowSignature(abs);

        CacheEntry e = cache.get(abs);
        if (cache.isValid(e, sig)) {
            return new SizeResult(e.sizeBytes, true);
        }

        long size = fastFolderSize(abs, cancel);
        cache.put(abs, size, sig);
        return new SizeResult(size, false);
    }

    private long fastFolderSize(Path root, AtomicBoolean cancel) {
        final LongAdder total = new LongAdder();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                            if (Files.isSymbolicLink(d)) return FileVisitResult.SKIP_SUBTREE;
                            return cancel.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                            if (cancel.get()) return FileVisitResult.TERMINATE;
                            if (a.isRegularFile()) total.add(a.size());
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path f, IOException e) {
                            return cancel.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignored) {}
        return total.sum();
    }

    private static List<Item> joinItems(AtomicBoolean cancel, List<CompletableFuture<Item>> futures) {
        List<Item> items = new ArrayList<>(futures.size());
        for (CompletableFuture<Item> cf : futures) {
            if (cancel.get()) return List.of();
            try { items.add(cf.get()); } catch (Exception ignored) {}
        }
        return items;
    }

    private static void permanentDelete(Path p) throws IOException {
        if (Files.notExists(p)) return;
        Files.walkFileTree(p, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                if (Files.isSymbolicLink(d)) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.deleteIfExists(f);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ------------------------------------------------------------------------

    public void shutdownNow() { exec.shutdownNow(); }
    @Override public void close() { shutdownNow(); }
}

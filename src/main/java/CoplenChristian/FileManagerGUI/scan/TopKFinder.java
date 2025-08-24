package CoplenChristian.FileManagerGUI.scan;

import CoplenChristian.FileManagerGUI.scan.FolderScanner.Item;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds the top-K largest folders under a given root.
 * Ensures results are non-nesting (won't return both an ancestor and its descendant).
 */
public final class TopKFinder {

    /** Compute top-K largest subfolders under {@code root}. */
    public static List<Item> findTopK(Path root, int k, AtomicBoolean cancel) throws IOException {
        final Path normalizedRoot = root.toAbsolutePath().normalize();

        Deque<Path> pathStack = new ArrayDeque<>();
        Deque<Long> sizeStack = new ArrayDeque<>();
        final List<Item> top = new ArrayList<>(k);

        Files.walkFileTree(normalizedRoot, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (cancel != null && cancel.get()) return FileVisitResult.TERMINATE;
                if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;

                Path abs = dir.toAbsolutePath().normalize();
                if (!abs.startsWith(normalizedRoot)) return FileVisitResult.SKIP_SUBTREE;

                pathStack.push(abs);
                sizeStack.push(0L);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (cancel != null && cancel.get()) return FileVisitResult.TERMINATE;
                if (attrs.isRegularFile()) {
                    long cur = sizeStack.pop();
                    sizeStack.push(cur + attrs.size());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (cancel != null && cancel.get()) return FileVisitResult.TERMINATE;

                long dirSize = sizeStack.pop();
                Path cur = pathStack.pop();

                // propagate to parent
                if (!sizeStack.isEmpty()) {
                    long parent = sizeStack.pop();
                    sizeStack.push(parent + dirSize);
                }

                // Skip the root itself; we want only subfolders
                if (cur.equals(normalizedRoot)) return FileVisitResult.CONTINUE;

                Item candidate = new Item(String.valueOf(cur.getFileName()), cur, true, dirSize, false);

                // avoid nested results
                if (hasAncestorInTop(cur, top)) return FileVisitResult.CONTINUE;
                top.removeIf(existing -> isAncestor(cur, existing.path));

                top.add(candidate);
                top.sort(Comparator.comparingLong((Item i) -> i.sizeBytes).reversed());
                if (top.size() > k) top.subList(k, top.size()).clear();

                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return (cancel != null && cancel.get()) ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });

        return top;
    }

    /** Public for tests. Returns true if {@code ancestor} is a proper ancestor of {@code descendant}. */
    public static boolean isAncestor(Path ancestor, Path descendant) {
        Path a = ancestor.toAbsolutePath().normalize();
        Path d = descendant.toAbsolutePath().normalize();
        return !a.equals(d) && d.startsWith(a);
    }

    private static boolean hasAncestorInTop(Path p, List<Item> top) {
        for (Item it : top) if (isAncestor(it.path, p)) return true;
        return false;
    }

    private TopKFinder() {}
}

package i3.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;

public class FileVisitors {

    /**
     * Just records the files it encounters
     */
    public static class Files extends FilesTransformer<Path> {

        @Override
        protected Path accepts(Path file) {
            return file;
        }
    }

    /**
     * Stores readable objects from accepted files in a canonical Map
     * (duplicates with the same hashcode are never recorded). This class must
     * be overriden to modify the accepts(OUT) method to filter and transform
     * files (not directories)
     *
     * @param <OUT> the transformation for the file output
     */
    public static abstract class FilesTransformer<OUT> implements FileVisitor<Path> {

        public final Map<OUT, OUT> canonicalMap = new HashMap<OUT, OUT>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (java.nio.file.Files.isReadable(dir)) {
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!java.nio.file.Files.isReadable(file)) {
                return FileVisitResult.CONTINUE;
            }
            OUT out = accepts(file);
            if (out != null && !canonicalMap.containsKey(out)) {
                canonicalMap.put(out, out);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            LogManager.getLogger().warn("visitFileFailed: " + exc.getMessage());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        /**
         * Override this to accept the OUT form of the files in the list, return
         * null to skip.
         *
         * @param file
         * @return the transform of the file or null to skip it
         */
        protected abstract OUT accepts(Path file);
    }

    /**
     * Delete a file tree
     */
    public static final class DeleteTreeVisitor implements FileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            java.nio.file.Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            LogManager.getLogger().warn("visitFileFailed: " + exc.getMessage());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc == null) {
                java.nio.file.Files.deleteIfExists(dir);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}

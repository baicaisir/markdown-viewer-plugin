package io.jenkins.plugins.markdownviewer;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Path-traversal defense helper. All file system access must go through this class.
 */
final class SafePath {

    private SafePath() {}

    /**
     * Resolve {@code relative} against {@code root} and guarantee the result is inside {@code root}.
     * Returns {@code null} if the path escapes or the file does not exist / is not a regular file.
     */
    @CheckForNull
    static File resolveInside(@NonNull File root, @NonNull String relative) throws IOException {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\0")) {
            return null;
        }
        File canonicalRoot = root.getCanonicalFile();
        File candidate = new File(canonicalRoot, relative).getCanonicalFile();
        String rootPath = canonicalRoot.getPath() + File.separator;
        if (!candidate.getPath().equals(canonicalRoot.getPath())
                && !candidate.getPath().startsWith(rootPath)) {
            return null;
        }
        if (!candidate.isFile()) {
            return null;
        }
        return candidate;
    }

    /**
     * Walk {@code root} and return all files whose name ends with {@code suffix} (case-insensitive).
     * Paths are returned relative to {@code root}, using forward slashes.
     */
    @NonNull
    static List<String> listFiles(@NonNull File root, @NonNull String suffix) throws IOException {
        if (!root.isDirectory()) {
            return Collections.emptyList();
        }
        Path rootPath = root.toPath();
        List<String> out = new ArrayList<>();
        String lowerSuffix = suffix.toLowerCase(Locale.ROOT);
        try (Stream<Path> s = Files.walk(rootPath)) {
            s.filter(Files::isRegularFile)
             .filter(p -> {
                 Path name = p.getFileName();
                 return name != null
                         && name.toString().toLowerCase(Locale.ROOT).endsWith(lowerSuffix);
             })
             .map(p -> rootPath.relativize(p).toString().replace(File.separatorChar, '/'))
             .sorted()
             .forEach(out::add);
        }
        return out;
    }
}

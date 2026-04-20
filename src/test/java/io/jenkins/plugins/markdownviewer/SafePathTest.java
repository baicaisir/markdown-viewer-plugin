package io.jenkins.plugins.markdownviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SafePathTest {

    @Test
    void listsMarkdownFilesRecursively(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.md"), "# a");
        Path sub = tmp.resolve("docs");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("b.MD"), "# b");
        Files.writeString(tmp.resolve("c.txt"), "nope");

        List<String> files = SafePath.listFiles(tmp.toFile(), ".md");
        assertEquals(List.of("a.md", "docs/b.MD"), files);
    }

    @Test
    void rejectsPathTraversal(@TempDir Path tmp) throws Exception {
        Path secret = tmp.resolve("secret.md");
        Files.writeString(secret, "top secret");
        Path root = tmp.resolve("archive");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ok.md"), "fine");

        assertNotNull(SafePath.resolveInside(root.toFile(), "ok.md"));
        assertNull(SafePath.resolveInside(root.toFile(), "../secret.md"));
        assertNull(SafePath.resolveInside(root.toFile(), "/etc/passwd"));
        assertNull(SafePath.resolveInside(root.toFile(), ""));
        assertNull(SafePath.resolveInside(root.toFile(), "ok.md\u0000"));
    }

    @Test
    void rejectsNonexistentOrDirectoryTarget(@TempDir Path tmp) throws Exception {
        File root = tmp.toFile();
        new File(root, "sub").mkdirs();
        assertNull(SafePath.resolveInside(root, "sub"));
        assertNull(SafePath.resolveInside(root, "missing.md"));
    }
}

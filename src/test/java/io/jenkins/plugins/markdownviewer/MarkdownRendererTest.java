package io.jenkins.plugins.markdownviewer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownRendererTest {

    @BeforeEach
    void reset() {
        MarkdownRenderer.invalidateAll();
    }

    @Test
    void rendersHeadingsAndLists() {
        String html = MarkdownRenderer.render(null, "# Hi\n\n- a\n- b\n");
        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("<li>a</li>"));
    }

    @Test
    void rendersGfmTablesAndTaskLists() {
        String md = "| a | b |\n|---|---|\n| 1 | 2 |\n\n- [x] done\n- [ ] todo\n";
        String html = MarkdownRenderer.render(null, md);
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("type=\"checkbox\""));
    }

    @Test
    void stripsInlineScripts() {
        String md = "hello\n\n<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n";
        String html = MarkdownRenderer.render(null, md);
        assertFalse(html.toLowerCase().contains("<script"), "scripts must be stripped");
        assertFalse(html.toLowerCase().contains("onerror"), "event handlers must be stripped");
    }

    @Test
    void preservesFencedCodeBlockLanguageClass() {
        String md = "```java\nint x = 1;\n```\n";
        String html = MarkdownRenderer.render(null, md);
        assertTrue(html.contains("language-java"));
    }

    @Test
    void cacheReturnsSameOutput() {
        String md = "# cache test";
        String a = MarkdownRenderer.render("k", md);
        String b = MarkdownRenderer.render("k", md);
        assertTrue(a.equals(b));
    }
}

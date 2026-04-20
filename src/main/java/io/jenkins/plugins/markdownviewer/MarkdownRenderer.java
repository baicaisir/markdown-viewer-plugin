package io.jenkins.plugins.markdownviewer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.Arrays;
import java.util.List;

/**
 * Core Markdown → safe HTML rendering. Stateless, thread-safe, cached by (cacheKey, content-hash).
 */
public final class MarkdownRenderer {

    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;

    private static final String SYS_MAX_BYTES = "io.jenkins.plugins.markdownviewer.maxFileBytes";
    private static final String SYS_CACHE_SIZE = "io.jenkins.plugins.markdownviewer.cacheSize";

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;
    private static final PolicyFactory SANITIZER;
    private static final Cache<String, String> CACHE;

    static {
        MutableDataSet opts = new MutableDataSet();
        List<com.vladsch.flexmark.util.misc.Extension> extensions = Arrays.asList(
                TablesExtension.create(),
                TaskListExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                AnchorLinkExtension.create(),
                TocExtension.create(),
                EmojiExtension.create()
        );
        opts.set(Parser.EXTENSIONS, extensions);
        opts.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        opts.set(TocExtension.LEVELS, TocExtension.LEVELS.getDefaultValue());

        PARSER = Parser.builder(opts).build();
        RENDERER = HtmlRenderer.builder(opts).build();

        SANITIZER = Sanitizers.FORMATTING
                .and(Sanitizers.BLOCKS)
                .and(Sanitizers.LINKS)
                .and(Sanitizers.TABLES)
                .and(Sanitizers.IMAGES)
                .and(Sanitizers.STYLES)
                .and(new HtmlPolicyBuilder()
                        .allowElements("hr", "pre", "code", "del", "s", "input")
                        .allowAttributes("class").globally()
                        .allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6", "a", "div")
                        .allowAttributes("type", "checked", "disabled").onElements("input")
                        .allowAttributes("start", "reversed", "type").onElements("ol")
                        .allowAttributes("align").onElements("th", "td")
                        .allowAttributes("target", "rel").onElements("a")
                        .allowStandardUrlProtocols()
                        .toFactory());

        long cacheSize = Long.getLong(SYS_CACHE_SIZE, 256L);
        CACHE = Caffeine.newBuilder().maximumSize(cacheSize).build();
    }

    private MarkdownRenderer() {}

    public static long maxBytes() {
        return Long.getLong(SYS_MAX_BYTES, DEFAULT_MAX_BYTES);
    }

    /**
     * Render Markdown to sanitized HTML. Returns cached result if available.
     *
     * @param cacheKey stable identifier (e.g. {@code runId+"|"+path}); use {@code null} to skip cache
     * @param markdown raw markdown text
     */
    @NonNull
    public static String render(String cacheKey, @NonNull String markdown) {
        if (cacheKey != null) {
            String cached = CACHE.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        Node document = PARSER.parse(markdown);
        String rawHtml = RENDERER.render(document);
        String safeHtml = SANITIZER.sanitize(rawHtml);
        if (cacheKey != null) {
            CACHE.put(cacheKey, safeHtml);
        }
        return safeHtml;
    }

    /** For tests. */
    static void invalidateAll() {
        CACHE.invalidateAll();
    }
}

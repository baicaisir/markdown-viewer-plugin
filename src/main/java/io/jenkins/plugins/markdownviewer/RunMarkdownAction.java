package io.jenkins.plugins.markdownviewer;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Per-build sidebar entry: lists all archived .md files and renders the selected one.
 */
public class RunMarkdownAction implements RunAction2 {

    private transient Run<?, ?> run;

    public RunMarkdownAction() {}

    public RunMarkdownAction(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    @CheckForNull
    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        if (run == null || !hasAnyMarkdown()) {
            return null;
        }
        return "document.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.RunMarkdownAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "markdown";
    }

    // ---- view model ----

    @NonNull
    public List<String> getFiles() {
        if (run == null) {
            return Collections.emptyList();
        }
        try {
            return SafePath.listFiles(archiveRoot(), ".md");
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private boolean hasAnyMarkdown() {
        return !getFiles().isEmpty();
    }

    @NonNull
    private File archiveRoot() {
        return new File(run.getRootDir(), "archive");
    }

    // ---- stapler endpoints ----

    /**
     * GET /job/X/N/markdown/show?path=README.md
     */
    public HttpResponse doShow(StaplerRequest req, StaplerResponse rsp,
                               @QueryParameter("path") String path) throws Exception {
        if (run == null) {
            return HttpResponses.notFound();
        }
        run.getParent().checkPermission(Item.READ);
        if (path == null || path.isEmpty()) {
            return HttpResponses.redirectTo(".");
        }
        File file = SafePath.resolveInside(archiveRoot(), path);
        if (file == null) {
            return HttpResponses.notFound();
        }
        long size = file.length();
        long cap = MarkdownRenderer.maxBytes();
        if (size > cap) {
            rsp.setStatus(413);
            rsp.setContentType("text/plain; charset=utf-8");
            rsp.getWriter().printf("File too large: %d bytes (cap %d). Download raw instead.%n", size, cap);
            return HttpResponses.ok();
        }
        String md = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String cacheKey = run.getExternalizableId() + "|" + path + "|" + file.lastModified();
        String html = MarkdownRenderer.render(cacheKey, md);

        req.setAttribute("path", path);
        req.setAttribute("html", html);
        req.getView(this, "show.jelly").forward(req, rsp);
        return HttpResponses.ok();
    }

    /**
     * Raw download endpoint: /job/X/N/markdown/raw?path=README.md
     */
    public void doRaw(StaplerRequest req, StaplerResponse rsp,
                      @QueryParameter("path") String path) throws Exception {
        if (run == null || path == null) {
            rsp.sendError(404);
            return;
        }
        run.getParent().checkPermission(Item.READ);
        File file = SafePath.resolveInside(archiveRoot(), path);
        if (file == null) {
            rsp.sendError(404);
            return;
        }
        rsp.setContentType("text/markdown; charset=utf-8");
        rsp.setHeader("Content-Disposition", "inline");
        Files.copy(file.toPath(), rsp.getOutputStream());
    }

    // Delegate action override for indexOf iteration in Jelly
    public Action getAction() {
        return this;
    }
}

package io.jenkins.plugins.markdownviewer;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
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
 * Per-job sidebar entry: shows markdown files from the last successful build.
 */
public class JobMarkdownAction implements Action {

    private final Job<?, ?> job;

    public JobMarkdownAction(@NonNull Job<?, ?> job) {
        this.job = job;
    }

    @NonNull
    public Job<?, ?> getJob() {
        return job;
    }

    @CheckForNull
    public Run<?, ?> getReferenceBuild() {
        Run<?, ?> b = job.getLastSuccessfulBuild();
        if (b == null) {
            b = job.getLastBuild();
        }
        return b;
    }

    @Override
    public String getIconFileName() {
        if (!hasAnyMarkdown()) {
            return null;
        }
        return "notepad.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.JobMarkdownAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "markdown";
    }

    @NonNull
    public List<String> getFiles() {
        Run<?, ?> b = getReferenceBuild();
        if (b == null) {
            return Collections.emptyList();
        }
        try {
            return SafePath.listFiles(new File(b.getRootDir(), "archive"), ".md");
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private boolean hasAnyMarkdown() {
        return !getFiles().isEmpty();
    }

    public HttpResponse doShow(StaplerRequest req, StaplerResponse rsp,
                               @QueryParameter("path") String path) throws Exception {
        job.checkPermission(Item.READ);
        Run<?, ?> b = getReferenceBuild();
        if (b == null) {
            return HttpResponses.notFound();
        }
        if (path == null || path.isEmpty()) {
            return HttpResponses.redirectTo(".");
        }
        File root = new File(b.getRootDir(), "archive");
        File file = SafePath.resolveInside(root, path);
        if (file == null) {
            return HttpResponses.notFound();
        }
        long cap = MarkdownRenderer.maxBytes();
        if (file.length() > cap) {
            rsp.setStatus(413);
            rsp.setContentType("text/plain; charset=utf-8");
            rsp.getWriter().printf("File too large: %d bytes (cap %d).%n", file.length(), cap);
            return HttpResponses.ok();
        }
        String md = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String cacheKey = b.getExternalizableId() + "|" + path + "|" + file.lastModified();
        String html = MarkdownRenderer.render(cacheKey, md);
        req.setAttribute("path", path);
        req.setAttribute("html", html);
        req.setAttribute("referenceBuild", b);
        req.getView(this, "show.jelly").forward(req, rsp);
        return HttpResponses.ok();
    }
}

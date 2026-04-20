package io.jenkins.plugins.markdownviewer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.RootAction;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level dashboard aggregating archived Markdown files across all jobs.
 * URL: /jenkins/markdown/
 */
@Extension
public class MarkdownDashboardAction implements RootAction {

    @Override
    public String getIconFileName() {
        return "notepad.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.MarkdownDashboardAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "markdown";
    }

    /** View model entry: one job with its archived md files. */
    public static final class Entry {
        private final Job<?, ?> job;
        private final Run<?, ?> run;
        private final List<String> files;

        public Entry(Job<?, ?> job, Run<?, ?> run, List<String> files) {
            this.job = job;
            this.run = run;
            this.files = files;
        }

        public Job<?, ?> getJob() { return job; }
        public Run<?, ?> getRun() { return run; }
        public List<String> getFiles() { return files; }
    }

    @NonNull
    public List<Entry> getEntries() {
        List<Entry> out = new ArrayList<>();
        Jenkins j = Jenkins.get();
        for (Job<?, ?> job : j.getAllItems(Job.class)) {
            if (!job.hasPermission(Item.READ)) {
                continue;
            }
            Run<?, ?> b = job.getLastSuccessfulBuild();
            if (b == null) {
                b = job.getLastBuild();
            }
            if (b == null) {
                continue;
            }
            try {
                List<String> files = SafePath.listFiles(new File(b.getRootDir(), "archive"), ".md");
                if (!files.isEmpty()) {
                    out.add(new Entry(job, b, files));
                }
            } catch (IOException ignored) {
                // skip jobs whose archive cannot be read
            }
        }
        return out;
    }
}

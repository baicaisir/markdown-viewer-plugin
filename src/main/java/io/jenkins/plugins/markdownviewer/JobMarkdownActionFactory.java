package io.jenkins.plugins.markdownviewer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;

@Extension
public class JobMarkdownActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @NonNull
    @Override
    public Collection<? extends JobMarkdownAction> createFor(@NonNull Job target) {
        return Collections.singleton(new JobMarkdownAction(target));
    }
}

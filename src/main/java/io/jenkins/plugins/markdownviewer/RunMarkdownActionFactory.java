package io.jenkins.plugins.markdownviewer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;

import java.util.Collection;
import java.util.Collections;

@Extension
public class RunMarkdownActionFactory extends TransientActionFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override
    public Collection<? extends RunMarkdownAction> createFor(@NonNull Run target) {
        return Collections.singleton(new RunMarkdownAction(target));
    }
}

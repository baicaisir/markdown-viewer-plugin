package io.jenkins.plugins.markdownviewer;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Shell;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class RunMarkdownActionIntegrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void sidebarListsArchivedMarkdownAndRenders() throws Exception {
        assumeFalse("skip on windows — uses /bin/sh", isWindows());

        FreeStyleProject p = j.createFreeStyleProject("md-demo");
        p.getBuildersList().add(new Shell("printf '# Hello\\n\\n- a\\n- b\\n' > README.md"));
        p.getPublishersList().add(new ArtifactArchiver("**/*.md"));

        FreeStyleBuild b = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setJavaScriptEnabled(false);
        wc.getOptions().setCssEnabled(false);
        wc.getOptions().setThrowExceptionOnScriptError(false);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        HtmlPage list = wc.goTo(b.getUrl() + "markdown/");
        assertTrue(list.asNormalizedText().contains("README.md"));

        HtmlPage show = wc.goTo(b.getUrl() + "markdown/show?path=README.md");
        String text = show.asNormalizedText();
        assertTrue("expected rendered heading: " + text, text.contains("Hello"));
        assertTrue("expected list item a", text.contains("a"));
        assertTrue("expected list item b", text.contains("b"));
    }

    @Test
    public void traversalAttemptReturns404() throws Exception {
        assumeFalse(isWindows());
        FreeStyleProject p = j.createFreeStyleProject("md-traverse");
        p.getBuildersList().add(new Shell("echo '# ok' > README.md"));
        p.getPublishersList().add(new ArtifactArchiver("**/*.md"));
        FreeStyleBuild b = j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setJavaScriptEnabled(false);
        wc.getOptions().setCssEnabled(false);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        URL attackUrl = new URL(j.getURL(), b.getUrl() + "markdown/show?path=../../../config.xml");
        Page page = wc.getPage(new WebRequest(attackUrl));
        assertEquals(404, page.getWebResponse().getStatusCode());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

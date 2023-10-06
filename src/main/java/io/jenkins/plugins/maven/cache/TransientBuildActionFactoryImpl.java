package io.jenkins.plugins.maven.cache;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class TransientBuildActionFactoryImpl extends TransientProjectActionFactory {
    @Inject
    MavenCachePluginAction plugin;

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCachePluginAction.class);

    @Override
    public Collection<? extends Action> createFor(AbstractProject target) {
        return Collections.singleton(new ProjectActionImpl(target));
    }

    public class ProjectActionImpl implements Action {
        private final AbstractProject project;

        public ProjectActionImpl(AbstractProject project) {
            this.project = project;
        }

        public String getIconFileName() {
            return "";
        }

        public String getDisplayName() {
            return "Maven Build Cache";
        }

        public String getUrlName() {
            return "maven-cache-old";
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            plugin.handleCacheRequest(new File(this.project.getRootDir(), "maven-cache"), req, rsp);
        }
    }
}

package io.jenkins.plugins.maven.cache;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.*;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

@Extension
public class TransientBuildActionFactoryImpl extends TransientProjectActionFactory {
    @Inject
    MavenCachePlugin plugin;

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCachePlugin.class);

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
            return plugin.getIconFileName();
        }

        public String getDisplayName() {
            return "Maven Build Cache Manager";
        }

        public String getUrlName() {
            return "maven-cache";
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            plugin.handleCacheRequest(this.project.getRootDir(), req, rsp);
        }
    }
}

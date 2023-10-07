package io.jenkins.plugins.maven.cache;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.spi.SyncResolver;

import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenCachePluginAction implements Action, Describable<MavenCachePluginAction> { // extends Plugin

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCachePluginAction.class);

    private List<CacheEntry> cacheEntries;

    @Override
    public String getIconFileName() {
        return "foo.png";
    }

    @Override
    public String getDisplayName() {
        return "Maven Build Cache Manager";
    }

    @Override
    public String getUrlName() {
        return "maven-cache";
    }

    public static final String JELLY_RESOURCES_PATH = "/io/jenkins/plugins/maven/cache/MavenCachePluginAction/";

    private AbstractProject project;

    private List<String> pathParts;

    private String fileContent;

    private boolean fileFound;

    public MavenCachePluginAction() {
        //
    }

    public MavenCachePluginAction(AbstractProject project) {
        this.project = project;
    }

    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        File cacheRootDir = new File(this.project.getRootDir(), "maven-cache");
        LOGGER.debug("doDynamic");
        if (req.getPathInfo().contains(this.project.getName() + "/" + getUrlName() + "/repository/")) {
            handleCacheRequest(cacheRootDir, req, rsp);
            return;
        }

        String path = StringUtils.substringAfter(req.getPathInfo(), project.getName() + "/" + getUrlName() + "/");
        this.pathParts = path == null || path.isEmpty() ? Collections.emptyList() : Arrays.asList(path.split("/"));
        File cacheContentSearchRoot = new File(cacheRootDir, path);
        if (cacheContentSearchRoot.isFile()) {
            fileFound = true;
            fileContent = Files.readString(cacheContentSearchRoot.toPath());
            this.cacheEntries = Collections.emptyList();
        } else {
            File[] files = cacheContentSearchRoot.listFiles();
            if (files != null) {
                this.cacheEntries = Arrays.stream(files)
                        .map(file -> new CacheEntry(file.isDirectory(), shortenedPath(cacheContentSearchRoot,
                                file.getPath()), calculateHref(file, cacheRootDir)))
                        .collect(Collectors.toList());
            } else {
                this.cacheEntries = Collections.emptyList();
            }
        }


        req.getView(this,"view.jelly").forward(req, rsp);
    }

    public String getBaseUrl() {
        return Jenkins.get().getRootUrl() + project.getUrl() + "maven-cache/";
    }

    private String calculateHref(File file, File cacheRootDir) {
        return getBaseUrl() + StringUtils.removeStart(StringUtils.substringAfter(file.getPath(), cacheRootDir.getPath()), "/");
    }

    private String shortenedPath(File cacheRootDir, String filePath) {
        return StringUtils.removeStart(StringUtils.substringAfter(filePath, cacheRootDir.getPath()), "/");
    }

    public String getFileContent() {
        return fileContent;
    }

    public boolean isFileFound() {
        return fileFound;
    }

    public List<String> getPathParts() {
        return pathParts;
    }

    public List<CacheEntry> getCacheEntries() {
        return cacheEntries;
    }

    public static class CacheEntry {
        private boolean directory;
        private String path;

        private String href;

        public CacheEntry(boolean directory, String path, String href) {
            this.directory = directory;
            this.path = path;
            this.href = href;
        }

        public boolean isDirectory() {
            return directory;
        }

        public void setDirectory(boolean directory) {
            this.directory = directory;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getHref() {
            return href;
        }

        public void setHref(String href) {
            this.href = href;
        }
    }

    public void handleCacheRequest(File rootDir, HttpServletRequest req, HttpServletResponse rsp)
            throws IOException, ServletException {
        String httpMethod = req.getMethod();
        LOGGER.debug("request {}:{}", req.getMethod(), req.getPathInfo());
        // request received
        // /job/foo/maven-cache/v1/org.eclipse.jetty/jetty-project/acf62bbb7e828406/buildinfo.xml
        String filePath = StringUtils.substringAfter(req.getPathInfo(), "/maven-cache/repository/");

        File destFile = new File(rootDir, filePath);
        LOGGER.debug("destFile: {}", destFile);
        switch (httpMethod) {
            case "GET":
                if (!Files.exists(destFile.toPath())) {
                    rsp.setStatus(404);
                    return;
                }
                IOUtils.copy(Files.newInputStream(destFile.toPath()), rsp.getOutputStream());
                return;
            case "PUT":
                // create tmp file to store content then atomic move
                Path tmp = Files.createTempFile("maven", "cache");
                IOUtils.copy(req.getInputStream(), Files.newOutputStream(tmp));
                Files.createDirectories(destFile.getParentFile().toPath());
                Files.move(tmp, destFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            default:
                LOGGER.error("received non implemented method: {}", httpMethod);
                rsp.setStatus(501);
        }
    }

    @Override
    public Descriptor<MavenCachePluginAction> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(MavenCachePluginAction.class);
    }

    @Extension
    public static class ActionInjector extends TransientActionFactory<AbstractProject> {
        @NonNull
        @Override
        public Collection<MavenCachePluginAction> createFor(AbstractProject p) {
            return Collections.singletonList(new MavenCachePluginAction(p));
        }

        @Override
        public Class type() {
            return AbstractProject.class;
        }
    }

    @Extension
    public static final class ToDeclarativeActionDescriptor extends Descriptor<MavenCachePluginAction> {
        // no op
    }

}

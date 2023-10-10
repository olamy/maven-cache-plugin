/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.jenkins.plugins.maven.cache;

import static hudson.Functions.checkPermission;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenCachePluginAction implements Action, Describable<MavenCachePluginAction> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCachePluginAction.class);

    private List<CacheEntry> cacheEntries = new ArrayList<>();

    public static final Permission MAVEN_CACHE_WRITE = new Permission(
            Item.PERMISSIONS,
            "MavenCacheWrite",
            Messages._permission_write_description(),
            Permission.WRITE,
            PermissionScope.JENKINS);

    public static final Permission MAVEN_CACHE_READ = new Permission(
            Item.PERMISSIONS,
            "MavenCacheRead",
            Messages._permission_read_description(),
            MAVEN_CACHE_WRITE,
            PermissionScope.JENKINS);

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
        checkPermission(this.project, MAVEN_CACHE_READ);
        File cacheRootDir = new File(this.project.getRootDir(), "maven-cache");
        LOGGER.debug("doDynamic");
        if (req.getPathInfo().contains(this.project.getName() + "/" + getUrlName() + "/repository/")) {
            handleCacheRequest(cacheRootDir, req, rsp);
            return;
        }

        String path = StringUtils.substringAfter(req.getPathInfo(), project.getName() + "/" + getUrlName() + "/");
        this.pathParts = path == null || path.isEmpty() ? Collections.emptyList() : Arrays.asList(path.split("/"));
        if (path != null) {
            File cacheContentSearchRoot = new File(cacheRootDir, path);
            if (cacheContentSearchRoot.isFile()) {
                fileFound = true;
                fileContent = Files.readString(cacheContentSearchRoot.toPath());
            } else {
                File[] files = cacheContentSearchRoot.listFiles();
                if (files != null) {
                    this.cacheEntries = Arrays.stream(files)
                            .map(file -> new CacheEntry(
                                    file.isDirectory(),
                                    shortenedPath(cacheContentSearchRoot, file.getPath()),
                                    calculateHref(file, cacheRootDir)))
                            .collect(Collectors.toList());
                }
            }
        }
        req.getView(this, "view.jelly").forward(req, rsp);
    }

    public String getBaseUrl() {
        return Jenkins.get().getRootUrl() + project.getUrl() + "maven-cache/";
    }

    private String calculateHref(File file, File cacheRootDir) {
        return getBaseUrl()
                + StringUtils.removeStart(StringUtils.substringAfter(file.getPath(), cacheRootDir.getPath()), "/");
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
                checkPermission(this.project, MAVEN_CACHE_WRITE);
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
            MavenCacheProjectProperty projectProperty =
                    (MavenCacheProjectProperty) p.getProperty(MavenCacheProjectProperty.class);
            return (projectProperty != null && projectProperty.isEnable())
                    ? Collections.singletonList(new MavenCachePluginAction(p))
                    : Collections.emptyList();
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

package io.jenkins.plugins.maven.cache;

import hudson.Extension;
import hudson.Plugin;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Extension
public class MavenCachePlugin extends Plugin implements RootAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCachePlugin.class);

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Maven Build Cache Manager";
    }

    @Override
    public String getUrlName() {
        return "plugin/maven-cache";
    }

    @Override
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        handleCacheRequest(new File(Jenkins.get().getRootDir(), "maven-cache"), req, rsp);
    }

    public void handleCacheRequest(File rootDir, HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
        String httpMethod = req.getMethod();
        LOGGER.debug("request {}:{}",req.getMethod(), req.getPathInfo());
        // request received
        // /job/foo/maven-cache/v1/org.eclipse.jetty/jetty-project/acf62bbb7e828406/buildinfo.xml
        String filePath = StringUtils.substringAfter(req.getPathInfo(), "/maven-cache/");

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

}

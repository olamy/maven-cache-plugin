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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.PersistentDescriptor;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenCacheCleanerTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenCacheCleanerTrigger.class);

    private int expirationDays = 7;

    private AbstractProject<?, ?> project;

    @DataBoundConstructor
    public MavenCacheCleanerTrigger(@NonNull String spec, int expirationDays) {
        super(spec);
        this.expirationDays = expirationDays;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        this.project = project;
    }

    @Override
    public void run() {
        if (project == null) {
            // TODO log error?
            return;
        }
        File cacheRootDir = new File(this.project.getRootDir(), "maven-cache");
        try {
            Files.walkFileTree(cacheRootDir.toPath(), new MavenCacheFileVisitor(expirationDays));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Apache Maven Cache cleaner trigger done");
    }

    public int getExpirationDays() {
        return expirationDays;
    }

    private static class MavenCacheFileVisitor implements FileVisitor<Path> {

        private final int expirationDays;

        public MavenCacheFileVisitor(int expirationDays) {
            this.expirationDays = expirationDays;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (Files.getLastModifiedTime(file).toMillis()
                    < (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(this.expirationDays))) {
                try {
                    LOGGER.debug("deleting too old file {}", file);
                    Files.delete(file);
                } catch (Exception e) {
                    LOGGER.warn("ignore fail to delete file {}", file);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            File[] children = dir.toFile().listFiles();
            if (children == null || children.length == 0) {
                // delete empty directory
                LOGGER.debug("deleting empty directory");
                Files.delete(dir);
            }
            return FileVisitResult.CONTINUE;
        }
    }

    @Extension
    @Symbol("mavenCacheCleaner")
    public static class DescriptorImpl extends TriggerDescriptor implements PersistentDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            if (item instanceof AbstractProject) {
                MavenCacheProjectProperty projectProperty = (MavenCacheProjectProperty)
                        ((AbstractProject) item).getProperty(MavenCacheProjectProperty.class);
                if (projectProperty != null && projectProperty.isEnable()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "Maven Cache Clean trigger";
        }
    }
}

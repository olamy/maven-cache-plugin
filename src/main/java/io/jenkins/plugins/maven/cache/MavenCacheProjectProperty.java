package io.jenkins.plugins.maven.cache;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class MavenCacheProjectProperty extends JobProperty<Job<?, ?>> {

    private boolean enable;

    private String cronSpec;

    private int expirationDays = 7;

    @DataBoundConstructor
    public MavenCacheProjectProperty(boolean enable, String cronSpec, int expirationDays) {
        this.enable = enable;
        this.cronSpec = cronSpec;
        this.expirationDays = expirationDays;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getCronSpec() {
        return cronSpec;
    }

    public void setCronSpec(String cronSpec) {
        this.cronSpec = cronSpec;
    }

    public int getExpirationDays() {
        return expirationDays;
    }

    public void setExpirationDays(int expirationDays) {
        this.expirationDays = expirationDays;
    }

    public String getCacheUrl() {
        return "maven-cache/repository";
    }

    @Extension
    @Symbol({"mavenCache"})
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public DescriptorImpl() {
            super();
        }
    }
}

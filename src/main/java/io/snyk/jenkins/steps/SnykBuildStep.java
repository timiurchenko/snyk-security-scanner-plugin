package io.snyk.jenkins.steps;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.snyk.jenkins.credentials.SnykApiToken;
import io.snyk.jenkins.tools.SnykInstallation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.allOf;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static hudson.Util.fixEmptyAndTrim;
import static java.util.logging.Level.FINE;
import static java.util.logging.Logger.getLogger;

public class SnykBuildStep extends Builder {

  private static final Logger LOG = getLogger(SnykBuildStep.class.getName());

  private boolean failOnIssues = true;
  private boolean monitorProjectOnBuild = true;
  private Severity severity = Severity.LOW;
  private String snykTokenId;
  private String targetFile;
  private String organisation;
  private String projectName;
  private String snykInstallation;

  @DataBoundConstructor
  public SnykBuildStep() {
  }

  @SuppressWarnings("unused")
  public boolean isFailOnIssues() {
    return failOnIssues;
  }

  @DataBoundSetter
  public void setFailOnIssues(boolean failOnIssues) {
    this.failOnIssues = failOnIssues;
  }

  @SuppressWarnings("unused")
  public boolean isMonitorProjectOnBuild() {
    return monitorProjectOnBuild;
  }

  @DataBoundSetter
  public void setMonitorProjectOnBuild(boolean monitorProjectOnBuild) {
    this.monitorProjectOnBuild = monitorProjectOnBuild;
  }

  @SuppressWarnings("unused")
  public String getSeverity() {
    return severity != null ? severity.getSeverity() : null;
  }

  @DataBoundSetter
  public void setSeverity(String severity) {
    this.severity = Severity.getIfPresent(severity);
  }

  @SuppressWarnings("unused")
  public String getSnykTokenId() {
    return snykTokenId;
  }

  @DataBoundSetter
  public void setSnykTokenId(String snykTokenId) {
    this.snykTokenId = snykTokenId;
  }

  @SuppressWarnings("unused")
  public String getTargetFile() {
    return targetFile;
  }

  @DataBoundSetter
  public void setTargetFile(String targetFile) {
    this.targetFile = targetFile;
  }

  @SuppressWarnings("unused")
  public String getOrganisation() {
    return organisation;
  }

  @DataBoundSetter
  public void setOrganisation(String organisation) {
    this.organisation = organisation;
  }

  @SuppressWarnings("unused")
  public String getProjectName() {
    return projectName;
  }

  @DataBoundSetter
  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  @SuppressWarnings("unused")
  public String getSnykInstallation() {
    return snykInstallation;
  }

  @DataBoundSetter
  public void setSnykInstallation(String snykInstallation) {
    this.snykInstallation = snykInstallation;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    return super.perform(build, launcher, listener);
  }

  @Extension
  @SuppressWarnings("unused")
  public static class SnykBuildStepDescriptor extends BuildStepDescriptor<Builder> {

    @CopyOnWrite
    private volatile SnykInstallation[] installations = new SnykInstallation[0];

    public SnykBuildStepDescriptor() {
      load();
    }

    @Nonnull
    @Override
    public String getDisplayName() {
      return "Invoke Snyk Security task";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    public SnykInstallation[] getInstallations() {
      return installations;
    }

    public void setInstallations(SnykInstallation... installations) {
      this.installations = installations;
      save();
    }

    public boolean hasInstallationsAvailable() {
      if (LOG.isLoggable(FINE)) {
        LOG.log(FINE, "configured snyk installations: {0}", installations.length);
        for (SnykInstallation installation : installations) {
          LOG.log(FINE, "- details: {0}", installation);
        }
      }
      return installations.length > 0;
    }

    public ListBoxModel doFillSeverityItems() {
      ListBoxModel model = new ListBoxModel();
      Stream.of(Severity.values())
            .map(Severity::getSeverity)
            .forEach(model::add);
      return model;
    }

    public ListBoxModel doFillSnykTokenIdItems(@AncestorInPath Item item, @QueryParameter String snykTokenId) {
      StandardListBoxModel model = new StandardListBoxModel();
      if (item == null) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && !jenkins.hasPermission(Jenkins.ADMINISTER)) {
          return model.includeCurrentValue(snykTokenId);
        }
      } else {
        if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
          return model.includeCurrentValue(snykTokenId);
        }
      }
      return model.includeEmptyValue()
                  .includeAs(ACL.SYSTEM, item, SnykApiToken.class)
                  .includeCurrentValue(snykTokenId);
    }

    public FormValidation doCheckSnykTokenId(@QueryParameter String value) {
      if (fixEmptyAndTrim(value) == null) {
        return FormValidation.error("Snyk API token is required.");
      } else {
        if (null == CredentialsMatchers.firstOrNull(lookupCredentials(SnykApiToken.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList()),
                                                    allOf(CredentialsMatchers.withId(value), CredentialsMatchers.instanceOf(SnykApiToken.class)))) {
          return FormValidation.error("Cannot find currently selected Snyk API token.");
        }
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckProjectName(@QueryParameter String value, @QueryParameter String monitorProjectOnBuild) {
      if (fixEmptyAndTrim(value) != null && "false".equals(fixEmptyAndTrim(monitorProjectOnBuild))) {
        return FormValidation.warning("Project name will be ignored, because the project is not monitored on build.");
      }
      return FormValidation.ok();
    }
  }
}
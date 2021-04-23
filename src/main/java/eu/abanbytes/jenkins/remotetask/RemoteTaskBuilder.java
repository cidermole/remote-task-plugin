package eu.abanbytes.jenkins.remotetask;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RemoteTaskBuilder extends Builder implements SimpleBuildStep {
    private final List<String> command;

    @DataBoundConstructor
    public RemoteTaskBuilder(List<String> command) {
        this.command = new ArrayList(command);
    }

    public List<String> getCommand() {
        return command;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("running: " + StringUtils.join(command, " "));

        // start process
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();

        // pass through process output
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.getLogger().println(line);
            }
        }
    }

    @Symbol("remoteTask")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckCommand(@QueryParameter List<String> command)
                throws IOException, ServletException {
            if (command == null || command.size() == 0) {
                return FormValidation.error(Messages.RemoteTaskBuilder_DescriptorImpl_errors_missingCommand());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.RemoteTaskBuilder_DescriptorImpl_DisplayName();
        }

    }

}

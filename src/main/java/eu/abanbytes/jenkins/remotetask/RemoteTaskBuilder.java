package eu.abanbytes.jenkins.remotetask;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.security.MasterToSlaveCallable;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Helps running commands via ProcessBuilder() remotely on an Agent.
 *
 * The command runs directly, without any additional environment from Jenkins.
 */
public class RemoteTaskBuilder extends Builder implements SimpleBuildStep {
    private final List<String> command;

    @DataBoundConstructor
    public RemoteTaskBuilder(List<String> command) {
        this.command = new ArrayList<>(command);
    }

    public List<String> getCommand() {
        return command;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("running: " + StringUtils.join(command, " "));

        VirtualChannel channel = launcher.getChannel();
        if(channel == null)
            throw new IOException("could not get channel to remote agent");

        // run process remotely
        List<String> resultLines = channel.call(new RemoteTaskCommand(command));
        PrintStream logger = listener.getLogger();
        for(String line : resultLines) {
            logger.println(line);
        }
    }

    /** serializable wrapper that is sent to the Agent, then its results back to the Master */
    private static class RemoteTaskCommand extends MasterToSlaveCallable<List<String>, IOException> {
        private static final long serialVersionUID = 1L;

        private final List<String> command;

        public RemoteTaskCommand(List<String> command) {
            this.command = new ArrayList<>(command);
        }

        @Override
        public List<String> call() throws IOException {
            // start process
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();

            // return process output
            List<String> resultLines = new ArrayList<>();
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    resultLines.add(line);
                }
            }
            return resultLines;
        }
    }

    @Symbol("remoteTask")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckCommand(@QueryParameter List<String> command) throws IOException, ServletException {
            if (command == null || command.size() == 0) {
                return FormValidation.error("Please pass a command to remoteTask()");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Run command (RemoteTask)";
        }
    }
}

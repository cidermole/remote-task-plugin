package eu.abanbytes.jenkins.remotetask;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Helps running commands via ProcessBuilder() remotely on an Agent.
 *
 * The command runs directly, without any additional environment from Jenkins.
 */
public class RemoteTaskStep extends Step {
    private final List<String> command;
    private boolean returnStdout = false;
    private boolean returnStatus = false;
    private String encoding = "";

    @DataBoundConstructor
    public RemoteTaskStep(List<String> command) {
        this.command = new ArrayList<>(command);
    }

    public List<String> getCommand() {
        return command;
    }

    @DataBoundSetter
    public void setReturnStdout(boolean returnStdout) {
        this.returnStdout = returnStdout;
    }

    @DataBoundSetter
    public void setReturnStatus(boolean returnStatus) {
        this.returnStatus = returnStatus;
    }

    @DataBoundSetter
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new RemoteTaskStep.Execution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<>(Arrays.asList(
                    TaskListener.class,
                    Launcher.class
            ));
        }

        @Override
        public String getFunctionName() {
            return "remoteTask";
        }

        @Override
        public String getDisplayName() {
            return "Run command";
        }
    }

    public static class Execution extends SynchronousNonBlockingStepExecution {
        protected static final long serialVersionUID = 1L;

        protected final transient RemoteTaskStep step;

        public Execution(RemoteTaskStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            String encoding = step.encoding;
            if(encoding.equals("")) { encoding = System.getProperty("file.encoding"); }

            TaskListener listener = Objects.requireNonNull(getContext().get(TaskListener.class));

            listener.getLogger().println("running: " + StringUtils.join(step.command, " "));

            // start process
            Process p = new ProcessBuilder(step.command).redirectErrorStream(true).start();

            // return process output
            List<String> resultLines = new ArrayList<>();
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), encoding))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    resultLines.add(line);
                    if(!step.returnStdout) { listener.getLogger().println(line); }
                }
            }
            int rc = p.exitValue();

            if(rc != 0 && !step.returnStatus) { throw new RuntimeException("command failed with return code " + rc); }

            if(step.returnStdout) {
                return StringUtils.join(resultLines, System.getProperty("line.separator"));
            }
            return rc;
        }
    }
}

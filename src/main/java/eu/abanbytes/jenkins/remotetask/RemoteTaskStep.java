package eu.abanbytes.jenkins.remotetask;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.*;

/**
 * Helps running commands via ProcessBuilder() remotely on an Agent.
 *
 * The command runs directly, without any additional environment from Jenkins.
 */
public class RemoteTaskStep extends Step implements Serializable {
    protected static final long serialVersionUID = 1L;
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

    /** contains the step implementation */
    public static class Execution extends SynchronousNonBlockingStepExecution {
        protected static final long serialVersionUID = 1L;

        @SuppressFBWarnings // the RemoteTaskStep.Execution object remains on the Jenkins master, is never serialized
        protected final transient RemoteTaskStep step;

        public Execution(RemoteTaskStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Object run() throws Exception {
            TaskListener listener = Objects.requireNonNull(getContext().get(TaskListener.class));
            Launcher launcher = Objects.requireNonNull(getContext().get(Launcher.class));

            listener.getLogger().println("running: " + StringUtils.join(step.command, " "));

            VirtualChannel channel = launcher.getChannel();
            if(channel == null)
                throw new IOException("could not get channel to remote agent");

            // run process remotely
            RemoteTaskResult result = channel.call(new RemoteTaskCommand(step));
            int rc = result.returnCode;
            String resultText = StringUtils.join(result.resultLines, System.getProperty("line.separator"));

            if(!step.returnStdout) { listener.getLogger().println(resultText); }

            if(result.error != null) { throw result.error; }
            if(rc != 0 && !step.returnStatus) { throw new RuntimeException("command failed with return code " + rc); }

            if(step.returnStdout) {
                return resultText;
            }
            return rc;
        }
    }

    /** wraps the result returned by the Agent */
    private static class RemoteTaskResult implements Serializable {
        private static final long serialVersionUID = 1L;
        public List<String> resultLines = new ArrayList<>();
        public int returnCode = -1;
        public Exception error = null;
    }

    /** serializable wrapper that is sent to the Agent, then its results back to the Master */
    private static class RemoteTaskCommand extends MasterToSlaveCallable<RemoteTaskResult, IOException> {
        private static final long serialVersionUID = 1L;

        private final RemoteTaskStep step;

        public RemoteTaskCommand(RemoteTaskStep step) {
            this.step = step;
        }

        @Override
        public RemoteTaskResult call() throws IOException {
            RemoteTaskResult result = new RemoteTaskResult();

            try {
                String encoding = step.encoding;
                if (encoding.equals("")) {
                    encoding = System.getProperty("file.encoding");
                }

                // start process
                Process p = new ProcessBuilder(step.command).redirectErrorStream(true).start();

                // return process output
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), encoding))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.resultLines.add(line);
                    }
                }
                p.waitFor();
                result.returnCode = p.exitValue();
            } catch(Exception e) {
                result.error = e;
            }

            return result;
        }
    }

}

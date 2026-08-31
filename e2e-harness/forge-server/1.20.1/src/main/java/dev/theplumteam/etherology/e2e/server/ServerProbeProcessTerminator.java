package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * Ends the Loom userdev launcher after Forge finishes dispatching its stopped event.
 */
final class ServerProbeProcessTerminator {

    static final String EXIT_THREAD_NAME = "etherology-e2e-server-probe-exit";
    static final int FAILURE_EXIT_STATUS = 1;
    static final long SERVER_THREAD_JOIN_TIMEOUT_MILLIS = 30_000L;
    static final int SUCCESS_EXIT_STATUS = 0;

    private static final String LOOM_USERDEV_RUNTIME_KIND = "loom-userdev";

    private final AtomicBoolean exitScheduled = new AtomicBoolean();
    private final IntConsumer exitAction;
    private final long serverThreadJoinTimeoutMillis;

    private ServerProbeProcessTerminator(
            long serverThreadJoinTimeoutMillis,
            IntConsumer exitAction
    ) {
        if (serverThreadJoinTimeoutMillis <= 0L) {
            throw new IllegalArgumentException(
                    "The server-thread join timeout must be positive"
            );
        }
        this.exitAction = Objects.requireNonNull(exitAction, "exitAction");
        this.serverThreadJoinTimeoutMillis = serverThreadJoinTimeoutMillis;
    }

    static ServerProbeProcessTerminator forLoomUserdev(String runtimeKind) {
        if (!LOOM_USERDEV_RUNTIME_KIND.equals(runtimeKind)) {
            throw new IllegalArgumentException(
                    "The process terminator is restricted to Loom userdev"
            );
        }
        return new ServerProbeProcessTerminator(
                SERVER_THREAD_JOIN_TIMEOUT_MILLIS,
                System::exit
        );
    }

    static ServerProbeProcessTerminator forTesting(
            long serverThreadJoinTimeoutMillis,
            IntConsumer exitAction
    ) {
        return new ServerProbeProcessTerminator(
                serverThreadJoinTimeoutMillis,
                exitAction
        );
    }

    static int exitStatusForReport(JsonObject report) {
        if (report != null
                && report.has("status")
                && report.get("status").isJsonPrimitive()
                && "passed".equals(report.get("status").getAsString())) {
            return SUCCESS_EXIT_STATUS;
        }
        return FAILURE_EXIT_STATUS;
    }

    Thread schedule(Thread serverThread, int exitStatus) {
        if (exitStatus != SUCCESS_EXIT_STATUS && exitStatus != FAILURE_EXIT_STATUS) {
            throw new IllegalArgumentException("The process-exit status is unsupported");
        }
        Objects.requireNonNull(serverThread, "serverThread");
        if (!serverThread.isAlive()) {
            throw new IllegalArgumentException("The stopped-event server thread is not alive");
        }
        if (!exitScheduled.compareAndSet(false, true)) {
            throw new IllegalStateException("The process exit was already scheduled");
        }

        Thread exitThread = new Thread(
                () -> exitAfterServerThread(serverThread, exitStatus),
                EXIT_THREAD_NAME
        );
        exitThread.setDaemon(true);
        exitThread.start();
        return exitThread;
    }

    private void exitAfterServerThread(
            Thread serverThread,
            int requestedExitStatus
    ) {
        int actualExitStatus = requestedExitStatus;
        try {
            serverThread.join(serverThreadJoinTimeoutMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            actualExitStatus = FAILURE_EXIT_STATUS;
        }
        if (serverThread.isAlive()) {
            actualExitStatus = FAILURE_EXIT_STATUS;
        }
        exitAction.accept(actualExitStatus);
    }
}

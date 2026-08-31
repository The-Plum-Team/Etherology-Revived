package dev.theplumteam.etherology.e2e.server;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerProbeProcessTerminatorTest {

    @Test
    void exitsSuccessfullyOnlyAfterTheStoppedEventServerThreadEnds()
            throws InterruptedException {
        assertEquals(
                30_000L,
                ServerProbeProcessTerminator.SERVER_THREAD_JOIN_TIMEOUT_MILLIS
        );
        CountDownLatch releaseServerThread = new CountDownLatch(1);
        Thread serverThread = startHeldServerThread(releaseServerThread);
        CountDownLatch exitCalled = new CountDownLatch(1);
        AtomicInteger observedStatus = new AtomicInteger(-1);
        ServerProbeProcessTerminator terminator =
                ServerProbeProcessTerminator.forTesting(2_000L, status -> {
                    observedStatus.set(status);
                    exitCalled.countDown();
                });

        try {
            Thread exitThread = terminator.schedule(
                    serverThread,
                    ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS
            );

            assertEquals(ServerProbeProcessTerminator.EXIT_THREAD_NAME, exitThread.getName());
            assertTrue(exitThread.isDaemon());
            assertFalse(exitCalled.await(50L, TimeUnit.MILLISECONDS));
            releaseServerThread.countDown();
            serverThread.join(2_000L);
            assertFalse(serverThread.isAlive());
            assertTrue(exitCalled.await(2L, TimeUnit.SECONDS));
            assertEquals(
                    ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS,
                    observedStatus.get()
            );
            assertThrows(
                    IllegalStateException.class,
                    () -> terminator.schedule(
                            Thread.currentThread(),
                            ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS
                    )
            );
        } finally {
            releaseServerThread.countDown();
            serverThread.join(2_000L);
        }
    }

    @Test
    void convertsAJoinTimeoutToAFailureExit() throws InterruptedException {
        CountDownLatch releaseServerThread = new CountDownLatch(1);
        Thread serverThread = startHeldServerThread(releaseServerThread);
        CountDownLatch exitCalled = new CountDownLatch(1);
        AtomicInteger observedStatus = new AtomicInteger(-1);
        ServerProbeProcessTerminator terminator =
                ServerProbeProcessTerminator.forTesting(25L, status -> {
                    observedStatus.set(status);
                    exitCalled.countDown();
                });

        try {
            terminator.schedule(
                    serverThread,
                    ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS
            );

            assertTrue(exitCalled.await(2L, TimeUnit.SECONDS));
            assertEquals(
                    ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                    observedStatus.get()
            );
        } finally {
            releaseServerThread.countDown();
            serverThread.join(2_000L);
        }
    }

    @Test
    void convertsAnInterruptedJoinToAFailureExit() throws InterruptedException {
        CountDownLatch releaseServerThread = new CountDownLatch(1);
        Thread serverThread = startHeldServerThread(releaseServerThread);
        CountDownLatch exitCalled = new CountDownLatch(1);
        AtomicInteger observedStatus = new AtomicInteger(-1);
        ServerProbeProcessTerminator terminator =
                ServerProbeProcessTerminator.forTesting(2_000L, status -> {
                    observedStatus.set(status);
                    exitCalled.countDown();
                });

        try {
            Thread exitThread = terminator.schedule(
                    serverThread,
                    ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS
            );
            exitThread.interrupt();

            assertTrue(exitCalled.await(2L, TimeUnit.SECONDS));
            assertEquals(
                    ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                    observedStatus.get()
            );
        } finally {
            releaseServerThread.countDown();
            serverThread.join(2_000L);
        }
    }

    @Test
    void failedReportsRequestAFailureExit() throws InterruptedException {
        JsonObject failedReport = new JsonObject();
        failedReport.addProperty("status", "failed");
        assertEquals(
                ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                ServerProbeProcessTerminator.exitStatusForReport(failedReport)
        );

        CountDownLatch releaseServerThread = new CountDownLatch(1);
        Thread serverThread = startHeldServerThread(releaseServerThread);
        CountDownLatch exitCalled = new CountDownLatch(1);
        AtomicInteger observedStatus = new AtomicInteger(-1);
        ServerProbeProcessTerminator terminator =
                ServerProbeProcessTerminator.forTesting(2_000L, status -> {
                    observedStatus.set(status);
                    exitCalled.countDown();
                });

        try {
            terminator.schedule(
                    serverThread,
                    ServerProbeProcessTerminator.exitStatusForReport(failedReport)
            );
            releaseServerThread.countDown();

            assertTrue(exitCalled.await(2L, TimeUnit.SECONDS));
            assertEquals(
                    ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                    observedStatus.get()
            );
        } finally {
            releaseServerThread.countDown();
            serverThread.join(2_000L);
        }
    }

    @Test
    void passedAndMalformedReportsMapFailClosed() {
        JsonObject passedReport = new JsonObject();
        passedReport.addProperty("status", "passed");
        JsonObject failedReport = new JsonObject();
        failedReport.addProperty("status", "failed");

        assertEquals(
                ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS,
                ServerProbeProcessTerminator.exitStatusForReport(passedReport)
        );
        assertEquals(
                ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                ServerProbeProcessTerminator.exitStatusForReport(failedReport)
        );
        assertEquals(
                ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                ServerProbeProcessTerminator.exitStatusForReport(new JsonObject())
        );
        assertEquals(
                ServerProbeProcessTerminator.FAILURE_EXIT_STATUS,
                ServerProbeProcessTerminator.exitStatusForReport(null)
        );
    }

    @Test
    void rejectsNonUserdevAndUnsafeSchedulingArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerProbeProcessTerminator.forLoomUserdev("packaged-forge")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ServerProbeProcessTerminator.forTesting(0L, ignored -> {
                })
        );
        assertThrows(
                NullPointerException.class,
                () -> ServerProbeProcessTerminator.forTesting(1L, null)
        );
        ServerProbeProcessTerminator terminator =
                ServerProbeProcessTerminator.forTesting(1L, ignored -> {
                });
        assertThrows(
                NullPointerException.class,
                () -> terminator.schedule(
                        null,
                        ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> terminator.schedule(new Thread(), 2)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> terminator.schedule(
                        new Thread(),
                        ServerProbeProcessTerminator.SUCCESS_EXIT_STATUS
                )
        );
    }

    private static Thread startHeldServerThread(CountDownLatch releaseServerThread)
            throws InterruptedException {
        CountDownLatch serverThreadStarted = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> {
            serverThreadStarted.countDown();
            try {
                releaseServerThread.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "test-server-thread");
        serverThread.start();
        assertTrue(serverThreadStarted.await(2L, TimeUnit.SECONDS));
        return serverThread;
    }
}

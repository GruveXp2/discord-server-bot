package org.gruve;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ServerTimeout {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> timeoutTask;
    private static int timeoutSeconds = 0;

    public static void setTimeout(int seconds) {
        timeoutSeconds = seconds;
        if (timeoutTask == null || timeoutTask.isCancelled()) {
            timeoutTask = scheduler.scheduleAtFixedRate(() -> {
                timeoutSeconds -= 5;
                if (timeoutSeconds <= 0) {
                    timeoutTask.cancel(false);
                    Main.setServerStatus(ServerStatus.OFFLINE);
                }
            }, 5, 5, TimeUnit.SECONDS);
            Main.setServerStatus(ServerStatus.TIMEOUT);
            System.out.println("Timeout set to " + seconds + "s");
        }
    }

    public static void stopTimeout() {
        timeoutTask.cancel(false);
        Main.setServerStatus(ServerStatus.OFFLINE);
        timeoutSeconds = 0;
    }

    public static String getTimeoutString() {
        return Util.secondsToTimeString(timeoutSeconds);
    }
}

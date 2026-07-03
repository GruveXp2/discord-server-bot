package org.gruve;

import org.apache.commons.io.FileUtils;
import org.gruve.commands.ServerCommand;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class BackupHandler {
    static void backupCobblemon() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy.MM.dd");
        LocalDateTime now = LocalDateTime.now();

        long epoch = now.toEpochSecond(ZoneOffset.UTC);
        try {
            Files.writeString(Path.of(FileLoc.COBBLEMON_LAST_BACKUP_PATH), String.valueOf(epoch));
        } catch (IOException e) {
            e.printStackTrace();
        }

        String dateSuffix = " (" + now.format(formatter) + ")";
        String sourcePathStr = FileLoc.FABRIC_SERVER_FOLDER + "/Cobblemon";
        String targetPathStr = FileLoc.SERVER_BACKUP_PATH + "/Cobblemon" + dateSuffix;

        try {
            FileUtils.copyDirectory(new File(sourcePathStr), new File(targetPathStr));
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy directory: " + sourcePathStr + " -> " + targetPathStr);
        }
    }

    public static void backupCobblemonAsync() {
        CompletableFuture.runAsync(() -> {
            System.out.println("Starting Cobbmelon backup...");
            backupCobblemon();
        }).thenRun(() -> {
            ServerCommand.startServer("cobblemon");
            Main.setServerStatus(ServerStatus.BACKUP_FINISHED);
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
        Main.setServerStatus(ServerStatus.BACKING_UP);
    }

    public static boolean shouldBackup(int secondsAgoThreshold) {
        String lastEpochStr = "0";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(FileLoc.COBBLEMON_LAST_BACKUP_PATH), StandardCharsets.UTF_8))) {
            lastEpochStr = reader.readLine();
            if (lastEpochStr != null) {
                lastEpochStr = lastEpochStr.trim();
            } else lastEpochStr = "0";
        } catch (IOException e) {
            e.printStackTrace();
        }
        long lastEpoch = Long.parseLong(lastEpochStr);
        long currentEpoch = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long lastBackupSeconds = currentEpoch - lastEpoch;
        Main.lastBackupSeconds = lastBackupSeconds;
        return lastBackupSeconds > secondsAgoThreshold;
    }
}

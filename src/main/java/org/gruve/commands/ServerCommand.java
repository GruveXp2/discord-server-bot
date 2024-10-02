package org.gruve.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.gruve.Main;
import org.gruve.ServerCommunicator;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ServerCommand extends ListenerAdapter {

    private static Process serverProcess = null;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        switch (command) {
            case "open" -> {

                Button kingdoms = Button.secondary("open-kingdoms", "Kingdoms");
                Button tribes = Button.secondary("open-tribes", "Tribes");
                Button botbows = Button.secondary("open-botbows", "BotBows");
                Button sumo = Button.secondary("open-sumo", "Sumo");

                ActionRow actionRow = ActionRow.of(kingdoms, tribes, botbows, sumo);
                event.reply("Select server to open:")
                        .setComponents(actionRow)
                        .queue();
            }
            case "runcommand" -> handleMinecraftCommand(event);
            case "tribes", "tribe" -> {
                String subcommand = event.getSubcommandName();
                if (subcommand == null) {
                    event.reply("Usage: /tribes [stats | start]").queue();
                    return;
                }

                switch (subcommand) {
                    case "stats" -> handleStats(event);
                    default -> event.reply("Unknown subcommand!").queue();
                }
            }
        }
    }

    private void handleStats(@NotNull SlashCommandInteractionEvent event) {
        // Your code to handle the "stats" subcommand
        //Main.
        event.reply("Work in progress, when its done its gonna print out game stats just like when you do /tribe stats ingame").queue();
    }

    private void handleMinecraftCommand(@NotNull SlashCommandInteractionEvent event) {
        String commandString = event.getOption("command") != null ? event.getOption("command").getAsString() : "";
        if (commandString.isEmpty()) {
            event.reply("No command provided.").setEphemeral(true).queue();
        } else {
            event.reply("Command sent to server: `" + commandString + "`\n" +
                    ":yellow_circle: Sending to server...").queue(message -> {
                String result = ServerCommunicator.sendCommandToServerAndGetResult(commandString);
                if (result.startsWith("<e")) {
                    result = result.substring(4); // remove the <e#> and only show the error message
                    Main.updateStatusMessage(message, "red", result);
                } else {
                    Main.updateStatusMessage(message, "green", "Command sent successfully");
                }
            });
        }
    }

    private static void selectServer(String serverID) {
        try {
            // Command to run the Python script
            String[] command = {"python", "C:\\Users\\gruve\\Desktop\\select_server.py"};  // Ensure correct Python path

            // Create a ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);  // Combine error stream with output stream

            // Start the process
            Process process = pb.start();

            // Write the serverID to the Python script's stdin
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(serverID.getBytes());
                outputStream.flush();
            }

            // Wait for the process to complete with a reasonable timeout (e.g., 5 seconds)
            boolean finished = process.waitFor(1, TimeUnit.SECONDS);

            if (finished) {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                    Main.currentServer = serverID;
                }
            } else {
                process.destroy();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String startServer(String serverID) {
        if ((serverProcess != null && serverProcess.isAlive()) || ServerCommunicator.isServerRunning()) {
            if (Objects.equals(serverID, Main.currentServer)) {
                return "Server is already running.";
            } else {
                return "Another server is already running (" + Main.currentServer + ")";
            }
        }
        selectServer(serverID);
        System.out.println("Selected server: " + serverID);
        String out = "Selected server: " + serverID;

        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "start", "/b", "start_server.bat");
        processBuilder.directory(new File(Main.SERVER_BAT_PATH));
        try {
            serverProcess = processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        out += "\n:white_circle: Loading...";
        out = "/1" + out; // to update the status when its started
        return out;
    }
}

package org.gruve.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.gruve.*;
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
            case "open" -> handleServerOpen(event);
            case "restart" -> handleRestart(event);
            case "runcommand" -> handleMinecraftCommand(event);
            case "timeoutserver" -> handleTimeout(event);
            case "stopservertimeout" -> handleStopTimeout(event);
            case "tribes", "tribe" -> {
                String subcommand = event.getSubcommandName();
                if (subcommand == null) {
                    event.reply("Usage: /tribes [stats | start]").queue();
                    return;
                }

                if (subcommand.equals("stats")) {
                    handleStats(event);
                } else {
                    event.reply("Unknown subcommand!").queue();
                }
            }
        }
    }

    private void handleRestart(@NotNull SlashCommandInteractionEvent event) {
        Main.restartBot();
        event.reply("The bot will now restart and hopefully fix the bugs if there were any").queue();
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
        } else if (Objects.equals(Main.currentServer, "tribes") && commandString.contains("op ")) {
            event.reply("Command sent to server: `" + commandString + "`\n Nice try, being OP is not allowed in tribes").queue();
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

    private static void handleTimeout(@NotNull SlashCommandInteractionEvent event) {
        int hours = event.getOption("hrs") != null ? event.getOption("hrs").getAsInt() : 0;
        int minutes = event.getOption("min") != null ? event.getOption("min").getAsInt() : 0;
        int seconds = event.getOption("sec") != null ? event.getOption("sec").getAsInt() : 0;
        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
        if (totalSeconds < 1) event.reply("You must specify a positive amount of time").setEphemeral(true).queue();
        ServerTimeout.setTimeout(totalSeconds);
        event.reply("Successfully set server timeout to " + Util.secondsToTimeString(totalSeconds)).queue();
    }

    private static void handleStopTimeout(@NotNull SlashCommandInteractionEvent event) {
        if (Main.serverStatus == ServerStatus.TIMEOUT) {
            ServerTimeout.stopTimeout();
            event.reply("The server timeout was stopped, and the server is able to be opened").queue();
        } else {
            event.reply("Nothing happened, there was no timeout to stop").queue();
        }
    }

    private static void handleServerOpen(@NotNull SlashCommandInteractionEvent event) {
        if (Main.serverStatus == ServerStatus.TIMEOUT) {
            event.reply(":red_circle: The server is on a timeout and cannot be opened right now").queue(hook -> {
                // Save the channel ID immediately
                Main.lastStatusMessageChannelID = Long.parseLong(event.getChannel().getId());
                System.out.println("Sent in channel " + event.getChannel().getId());

                // Use the InteractionHook to retrieve the message after it has been sent
                hook.retrieveOriginal().queue(sentMessage -> {
                    // Save the message ID after the message is sent
                    Main.lastStatusMessageID = Long.parseLong(sentMessage.getId());
                    System.out.println("Message ID: " + sentMessage.getId());
                });
            });
        } else {
            Button kingdoms = Button.secondary("open-kingdoms", "Kingdoms");
            Button tribes = Button.secondary("open-tribes", "Tribes");
            Button botbows = Button.secondary("open-botbows", "BotBows");
            Button cobblemon = Button.secondary("open-cobblemon", "Cobblemon");

            ActionRow actionRow = ActionRow.of(kingdoms, tribes, botbows, cobblemon);
            event.reply("Select server to open:")
                    .setComponents(actionRow)
                    .queue();
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
        if (Main.serverStatus == ServerStatus.LOADING) {
            return "The server is already starting";
        }
        selectServer(serverID);
        System.out.println("Selected server: " + serverID);
        String out = "Selected server: " + serverID;

        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "start", "/b", "start_server.bat");
        processBuilder.directory(new File(
                serverID.equals("cobblemon") ? FileLoc.FABRIC_SERVER_BAT_FOLDER : FileLoc.SERVER_FOLDER));
        try {
            serverProcess = processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (serverID.equals("cobblemon")) {
            out += "\n:green_circle: Server is starting, will be open in about 30 seconds";
        } else {
            out += "\n:white_circle: Loading...";
            out = "/1" + out; // to update the status when its started
        }
        return out;
    }
}

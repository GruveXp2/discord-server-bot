package org.gruve;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.gruve.commands.ServerCommand;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    public static final String SERVER_BAT_PATH = "C:\\Users\\gruve\\Desktop\\Server";
    public static final String SERVER_IP = "localhost";
    public static final String DISCORD_STATUS_MESSAGE_ID_LOCATION = "C:\\Users\\gruve\\Desktop\\Server\\discord-server-status-message-id.txt";
    public static final int SERVER_PORT = 25566; // port used to communicate with the server plugin
    public static String currentServer = "N/A";
    public static String serverStatusMessage = "Loading...";
    public static ServerStatus serverStatus = ServerStatus.LOADING;
    public static int statusTime = 0; // how many seconds the current status has been
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    protected static JDA JDA;
    public static long lastStatusMessageChannelID;
    public static long lastStatusMessageID;
    public static final int STATUS_SCAN_INTERVAL = 5; // seconds

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Hello world!");
        JDA = JDABuilder.createDefault(Token.TOKEN).build();
        JDA.addEventListener(new Listeners());
        JDA.addEventListener(new ServerCommand());
        JDA.awaitReady();
        initServerStatusMessageIDs();

        scheduler.scheduleAtFixedRate(() -> {
            if (ServerCommunicator.isServerRunning()) { // if the server program is running on the pc
                //System.out.print("Minecraft server is running. Sending command...  "); // DEBUG
                String result = ServerCommunicator.sendCommandToServerAndGetResult("@ping");
                if (result.startsWith("<e2>")) { // public ip doesnt work
                    result = ServerCommunicator.sendCommandToServerAndGetResult("@ping", "localhost");
                    if (result.startsWith("<e2>")) { // localhost doesn't work either, so its offline and is starting
                        if (serverStatus == ServerStatus.OFFLINE || serverStatus == ServerStatus.STARTING || serverStatus == ServerStatus.LOADING) {
                            setServerStatus(ServerStatus.STARTING);
                        } else {
                            setServerStatus(ServerStatus.CLOSING);
                        }
                    } else { // localhost works, so that means the vpn is on
                        setServerStatus(ServerStatus.VPN);
                    }
                } else {
                    setServerStatus(ServerStatus.ONLINE);
                    setServerStatusMessage(result);
                }
                currentServer = ServerCommunicator.getCurrentServer();
            } else if (serverStatus == ServerStatus.TIMEOUT) {
                setServerStatus(ServerStatus.TIMEOUT);
            } else {
                setServerStatus(ServerStatus.OFFLINE);
                System.out.println("Set to OFFLINE bc pid isnt server");
            }
            statusTime += STATUS_SCAN_INTERVAL;
        }, 0, STATUS_SCAN_INTERVAL, TimeUnit.SECONDS);
    }

    private static void initServerStatusMessageIDs() { // reads the message and channel id from a file to update it
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(DISCORD_STATUS_MESSAGE_ID_LOCATION), StandardCharsets.UTF_8))) {
            lastStatusMessageChannelID = Long.parseLong(reader.readLine());
            lastStatusMessageID = Long.parseLong(reader.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveServerStatusMessageIDs() { // saves the message ids so if the bot restarts it still has access to the message
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DISCORD_STATUS_MESSAGE_ID_LOCATION))) {
            writer.write(Long.toString(lastStatusMessageChannelID));
            writer.newLine(); // Writes a newline character to separate the values
            writer.write(Long.toString(lastStatusMessageID));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateOpenMessage(String statusColor, String updatedStatus) {
        TextChannel channel = JDA.getTextChannelById(lastStatusMessageChannelID);
        if (channel == null) return;
        channel.retrieveMessageById(lastStatusMessageID).queue(message -> updateStatusMessage(message, statusColor, updatedStatus), throwable -> {
            // No action is required if the message does not exist or any other error occurs
            // Just a placeholder to meet the method signature requirement
        });
    }

    public static void updateStatusMessage(Message message, String statusColor, String newStatusMessage) {
        String originalContent = message.getContentRaw();

        // Replace instances of :<something>_circle: followed by any text with :newStatusColor_circle: and new text
        String modifiedContent = originalContent.replaceAll(":[^:]+_circle:.*", ":" + statusColor + "_circle: " + newStatusMessage);

        // Directly edit the message with the modified content
        message.editMessage(modifiedContent).queue();
        System.out.println("Status message updated: (" + statusColor + ") " + newStatusMessage);
    }

    public static void updateStatusMessage(InteractionHook hook, String statusColor, String newStatusMessage) {
        hook.retrieveOriginal().queue(originalMessage -> {
            String originalContent = originalMessage.getContentRaw();

            // Replace the circle and status text with the new color and message
            String modifiedContent = originalContent.replaceAll(":[^:]+_circle:.*", ":" + statusColor + "_circle: " + newStatusMessage);

            // Edit the original message with the modified content
            hook.editOriginal(modifiedContent).queue();
            System.out.println("Status message updated: (" + statusColor + ") " + newStatusMessage);
        });
    }

    public static void setServerStatus(ServerStatus status) {
        if (serverStatus == ServerStatus.PLUGIN_BUG && (status == ServerStatus.STARTING || status == ServerStatus.CLOSING)) {
            status = ServerStatus.PLUGIN_BUG;
        }
        if (status != serverStatus) {
            statusTime = 0;
            System.out.println("Status changed: " + serverStatus + " -> " + status);
        } else {
            System.out.println("Status unchanged: " + serverStatus);
            if (status == ServerStatus.OFFLINE || status == ServerStatus.CLOSING) {
                lastStatusMessageID = 0; // slutter å tracke melding
                lastStatusMessageChannelID = 0;
                return; // trenger ikke å fikse timer greier eller oppdatere melding pga den endres ikke uansett
            }
        }
        String time = Util.secondsToTimeString(statusTime);

        switch (status) {
            case LOADING -> {}
            case STARTING -> {
                if (statusTime >= 60) {
                    setServerStatus(ServerStatus.PLUGIN_BUG);
                    return;
                }
                Main.updateOpenMessage("yellow", "The server is starting... (" + time + ")");
                setServerStatusMessage("Starting server...");
            }
            case ONLINE -> Main.updateOpenMessage("green", "Server is open (" + time + ")");
            case VPN -> Main.updateOpenMessage("orange", "Server is open, " +
                    "but @Gruve uses his VPN. To be able to connect to the server, " +
                    "please tell him to turn off his VPN. (" + time + ")");
            case PLUGIN_BUG -> {
                updateOpenMessage("green", "Server is open, " +
                        "but the discord bot failed to connect to it (" + time + "). The plugin is probably outdated or bugged");
                setServerStatusMessage("Server online");
            }
            case CLOSING -> {
                updateOpenMessage("red", "Server closing...");
                setServerStatusMessage("Server closing...");
            }
            case OFFLINE -> {
                updateOpenMessage("red", "Server closed. Reopen by doing /open");
                setServerStatusMessage("Server offline");
            }
            case TIMEOUT -> {
                String timeoutLeft = ServerTimeout.getTimeoutString();
                updateOpenMessage("red", "Server is temporarily disabled(" + timeoutLeft + " left). @Gruve is probably busy with something or the server cant be on at the moment. Contact Colin or Gruve if you have questions");
                setServerStatusMessage("Server disabled (" + timeoutLeft + " left)");
            }
            case ERROR -> updateOpenMessage("red", "The server/plugin is bugged, please contact @Gruve");
        }
        serverStatus = status;
    }

    private static void setServerStatusMessage(String statusMessage) {
        if (Objects.equals(statusMessage, serverStatusMessage)) return;
        serverStatusMessage = statusMessage;
        JDA.getPresence().setActivity(Activity.customStatus(statusMessage));
    }
}
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
    public static String serverStatusInfo = "Loading...";
    public static String serverStatusMessage = "Loading...";
    public static ServerStatus serverStatus = ServerStatus.INITIALIZING;
    public static int lastStatusTime = 0; // how many seconds the last status has been, used for stuff like when it says server closed, it says for how long it was open
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

        scheduler.scheduleAtFixedRate(Main::pingServer, 0, STATUS_SCAN_INTERVAL, TimeUnit.SECONDS);
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

    private static void pingServer() {
        boolean processRunning = ServerCommunicator.isServerRunning(); // if the server program is running on the pc
        ServerConnectionStatus connection = ServerConnectionStatus.NO_CONNECTION;

        if (processRunning) {
            //System.out.print("Minecraft server is running. Sending command...  "); // DEBUG
            String result = ServerCommunicator.sendCommandToServerAndGetResult("@ping");
            if (!result.startsWith("<e2>")) { // public ip works
                connection = ServerConnectionStatus.FULL_CONNETCION;
                setServerStatusInfo(result);
            } else {
                result = ServerCommunicator.sendCommandToServerAndGetResult("@ping", "localhost");
                if (!result.startsWith("<e2>")) { // localhost works
                    connection = ServerConnectionStatus.LOCAL_CONNECTION;
                }
            }
            currentServer = ServerCommunicator.getCurrentServer();
        }
        statusTime += STATUS_SCAN_INTERVAL;
        processPing(processRunning, connection);
    }

    private static void processPing(boolean processRunning, ServerConnectionStatus status) {
        if (processRunning) {
            switch (status) {
                case NO_CONNECTION -> {
                    switch (serverStatus) {
                        case INITIALIZING, OFFLINE, LOADING, FILE_BUG, STARTING -> {
                            if (serverStatus == ServerStatus.STARTING && statusTime < 30) {
                                setServerStatus(ServerStatus.STARTING);
                            } else {
                                setServerStatus(ServerStatus.PLUGIN_BUG);
                            }
                        }
                        case PLUGIN_BUG -> setServerStatus(ServerStatus.PLUGIN_BUG);
                        case ONLINE, VPN, CLOSING -> setServerStatus(ServerStatus.CLOSING);
                        default -> setServerStatus(ServerStatus.ERROR);
                    }
                }
                case LOCAL_CONNECTION -> setServerStatus(ServerStatus.VPN);
                case FULL_CONNETCION -> setServerStatus(ServerStatus.ONLINE);
            }
        } else {
            switch (serverStatus) {
                case LOADING, FILE_BUG -> {
                    if (statusTime > 10) {
                        setServerStatus(ServerStatus.FILE_BUG);
                    }
                }
                case ONLINE, PLUGIN_BUG, VPN, STARTING -> setServerStatus(ServerStatus.CLOSING);
                case CLOSING -> {
                    lastStatusMessageID = 0; // slutter å tracke melding, skal ikke endre på meldinga når serveren er lukka
                    lastStatusMessageChannelID = 0;
                    saveServerStatusMessageIDs();

                    ServerCommunicator.resetServerPID(); // resetter server pid sånn at det ikke kommer opp at serveren er åpen når botten starter igjen
                    setServerStatus(ServerStatus.OFFLINE);
                }
                case OFFLINE, INITIALIZING -> setServerStatus(ServerStatus.OFFLINE);
                case TIMEOUT -> setServerStatus(ServerStatus.TIMEOUT);
                default -> setServerStatus(ServerStatus.ERROR);
            }
        }
    }

    private static boolean shouldResetTimer(ServerStatus current, ServerStatus next) {
        // Resett timer hvis serveren er av eller initializer
        if (current == ServerStatus.OFFLINE || current == ServerStatus.INITIALIZING) {
            return true;
        }

        // Resett timer hvis serveren lukkes
        if (next == ServerStatus.OFFLINE) {
            return true;
        }

        // Ikke resett hvis man går mellom forskjellige online statuser
        return (next == ServerStatus.ONLINE || next == ServerStatus.PLUGIN_BUG) &&
                current != ServerStatus.ONLINE && current != ServerStatus.PLUGIN_BUG;
    }

    public static void setServerStatus(ServerStatus status) {
        if (status != serverStatus) {
            if (shouldResetTimer(serverStatus, status)) {
                lastStatusTime = statusTime;
                statusTime = 0;
            }
            System.out.println("Status changed: " + serverStatus + " -> " + status);
        } else {
            System.out.println("Status unchanged: " + serverStatus);
        }
        String time = Util.secondsToTimeString(statusTime);

        switch (status) {
            case INITIALIZING -> {}
            case LOADING -> {
                Main.updateOpenMessage("yellow", "The server is starting... (" + time + ")");
                setServerStatusInfo("Starting server...");
            }
            case ONLINE -> Main.updateOpenMessage("green", "Server is open (" + time + ")");
            case VPN -> Main.updateOpenMessage("orange", "Server is open, " +
                    "but @Gruve uses his VPN. To be able to connect to the server, " +
                    "please tell him to turn off his VPN. (" + time + ")");
            case PLUGIN_BUG -> {
                if (Objects.equals(currentServer, "sumo")) {
                    updateOpenMessage("green", "Sumo server is open(" + time + "), " +
                            "but the plugin doesnt work at the moment, this is bc sumo will get merged into the botbows server Soon:tm:");
                    setServerStatusInfo("Server online");
                    return;
                }
                updateOpenMessage("green", "Server is open, " +
                        "but the discord bot failed to connect to it (" + time + "). The plugin is probably outdated or bugged");
                setServerStatusInfo("Server online");
            }
            case CLOSING -> {
                updateOpenMessage("red", "Server closing...");
                setServerStatusInfo("Server closing...");
            }
            case OFFLINE -> {
                updateOpenMessage("red", "Server closed after " + Util.secondsToTimeString(lastStatusTime) + ". Reopen by doing /open");
                setServerStatusInfo("Server offline");
            }
            case TIMEOUT -> {
                String timeoutLeft = ServerTimeout.getTimeoutString();
                updateOpenMessage("red", "Server is temporarily disabled(" + timeoutLeft + " left). @Gruve is probably busy with something or the server cant be on at the moment. Contact Colin or Gruve if you have questions");
                setServerStatusInfo("Server disabled (" + timeoutLeft + " left)");
            }
            case ERROR -> updateOpenMessage("red", "The server/plugin is bugged, please contact @Gruve");
        }
        serverStatus = status;
    }

    public static void updateOpenMessage(String statusColor, String updatedStatus) {
        if (serverStatusMessage.equals(updatedStatus)) return;
        serverStatusMessage = updatedStatus;
        TextChannel channel = JDA.getTextChannelById(lastStatusMessageChannelID);
        if (channel == null) return;
        channel.retrieveMessageById(lastStatusMessageID).queue(message -> updateStatusMessage(message, statusColor, updatedStatus), _ -> {
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

    private static void setServerStatusInfo(String statusMessage) {
        if (Objects.equals(statusMessage, serverStatusInfo)) return;
        serverStatusInfo = statusMessage;
        JDA.getPresence().setActivity(Activity.customStatus(statusMessage));
    }
}
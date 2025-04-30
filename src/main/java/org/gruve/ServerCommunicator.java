package org.gruve;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ServerCommunicator {

    public static boolean isServerRunning() {
        try {
            String pid;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(FileLoc.SERVER_PID_PATH), StandardCharsets.UTF_16))) {
                pid = reader.readLine();
                if (pid == null) return false;
                pid = pid.trim();
                if (pid.equals("0")) return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            //System.out.print(pid + ": ");
            String[] command = {
                    "tasklist",
                    "/FI", "PID eq " + pid,
                    "/FO", "CSV",
                    "/NH"
            };
            Process process = Runtime.getRuntime().exec(command);
            String output = new String(process.getInputStream().readAllBytes());
            return output.contains(pid);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void resetServerPID() { // saves the message ids so if the bot restarts it still has access to the message
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FileLoc.SERVER_PID_PATH))) {
            writer.write(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getCurrentServer() {
        String currentServer;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(FileLoc.SERVER_WORLD_ID_PATH), StandardCharsets.UTF_8))) {
            currentServer = reader.readLine();
            return currentServer;
        } catch (IOException e) {
            e.printStackTrace();
            return "Error";
        }
    }

    /**
     * Sends the command to the server. If it cant connect, it will return <strong>"cant_connect"</strong>
     */
    public static String sendCommandToServerAndGetResult(String command) {return sendCommandToServerAndGetResult(command, Main.SERVER_IP);}

    /**
     * Sends the command to the server. If it cant connect, it will return <strong>"<e2>Failed to connect to the server"</strong>
     */
    public static String sendCommandToServerAndGetResult(String command, String IP) {

        if (Objects.equals(command, "")) {
            return "Please enter a minecraft command";
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        try (Socket socket = new Socket(IP, Main.SERVER_PORT);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Set a timeout for the socket (2000 ms = 2 seconds)
            socket.setSoTimeout(2000);

            // Send the command to the server
            out.println(command);

            // Read the result from the server (with a 2-second timeout)
            String response = in.readLine();
            return Objects.requireNonNullElse(response, "<e1>Error: no response from server");
        } catch (SocketException e) {
            return "<e2>Failed to connect to the server"; // if the plugin havent implemented communication, it might say starting forever
        } catch (IOException e) {
            e.printStackTrace();
            return "<e3>" + e.getMessage();
        }
    }
}

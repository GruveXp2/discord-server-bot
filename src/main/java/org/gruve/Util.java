package org.gruve;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class Util {

    public static void replyToMessage(long channelID, long messageID, String reply) {
        // Get the TextChannel by ID
        TextChannel channel = Main.JDA.getTextChannelById(921820957268668486L);
        if (channel == null) {
            System.out.println("Channel not found!");
            return;
        }

        // Retrieve Yarin's message id
        RestAction<Message> messageAction = channel.retrieveMessageById(69);
        messageAction.queue(message -> {
            // Respond to the message
            message.reply(reply).queue();
        }, failure -> {
            // Handle failure (e.g., message not found)
            System.out.println("Failed to retrieve the message: " + failure.getMessage());
        });
    }

    public static String secondsToTimeString(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds / 60) % 60;
        String time = hours > 0 ? hours + "hr " : "";
        if (hours > 0 || minutes > 0) time += minutes + "min";
        if (totalSeconds < 120) {
            if (totalSeconds >= 60) {
                time += " ";
            }
            int seconds = totalSeconds % 60;
            time += seconds + "s";
        }
        return time;
    }

    public static String getPublicIP() {
        try {
            URI uri = new URI("https://api.ipify.org");
            URL url = uri.toURL();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return in.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }
}

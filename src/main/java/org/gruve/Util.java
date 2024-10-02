package org.gruve;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;

public class Util {

    public void replyToMessage(long channelID, long messageID, String reply) {
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

}

import net.dv8tion.jda.client.entities.Group;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by Simon on 2/25/2017.
 */
public class ZengBot extends ListenerAdapter {
    public static void main(String[] args) {
        /*
        Your user token should be in the discord.properties file located in the resources folder.
        Discord doesn't exactly provide an easy way to find your user token, so you'll have to sniff it from your
        network traffic yourself - here's a guide on how to do just that:
        https://github.com/Orangestar12/cacobot/wiki/How-to-switch-from-a-standard-user-to-a-Bot-account.
         */
        Properties discordProperties = getProperties("discord.properties");

        try {
            JDA jda = new JDABuilder(AccountType.BOT).setToken(discordProperties.getProperty("discord.token")).addListener(new ZengBot()).buildBlocking();
        } catch (LoginException e) {
            System.err.println("Token used: " + discordProperties.get("discord.token"));
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (RateLimitedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets properties from .properties files
     *
     * @param fileName - path and name of properties file
     * @return A Properties object with all the properties found in the file
     */
    private static Properties getProperties(String fileName) {
        Properties properties = new Properties();
        try {
            InputStream in = ZengBot.class.getResourceAsStream("/" + fileName);
            properties.load(in);

        } catch (IOException e) {
            System.err.println("There was an error reading " + fileName + ": " + e.getCause()
                    + " : " + e.getMessage());
            System.exit(1);
        }

        return properties;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        //These are provided with every event in JDA
        JDA jda = event.getJDA();                       //JDA, the core of the api.
        long responseNumber = event.getResponseNumber();//The amount of discord events that JDA has received since the last reconnect.

        //Event specific information
        User author = event.getAuthor();                //The user that sent the message
        Message message = event.getMessage();           //The message that was received.
        MessageChannel channel = event.getChannel();    //This is the MessageChannel that the message was sent to. This could be a TextChannel, PrivateChannel, or Group!

        String msg = message.getContent();              //This returns a human readable version of the Message. Similar to what you would see in the client.

        boolean bot = author.isBot();                     //This boolean is useful to determine if the User that sent the Message is a BOT or not!

        String debugOutput =
                "Author: " + author.getAsMention()
                + "\nAuthor ID: " + author.getId()
                + "\nMessage: " + msg
                + "\nAuthor is official bot: " + bot
                + "\nResponse number: " + responseNumber
                + "\nChannel: " + channel.getName() + "\n";
        System.out.println(debugOutput);

        if (msg.startsWith("-")) {
            String output = "[" + author.getName() + "] ";
            switch(msg) {
                case "-debug":
                    output += debugOutput;
                    break;
                case "-help":
                    output += "Available commands: -debug, -help";
                    break;
                default:
                    output += "Unknown command.";
            }
            channel.sendMessage(output).queue();
            message.deleteMessage().queue();
        }
    }
}

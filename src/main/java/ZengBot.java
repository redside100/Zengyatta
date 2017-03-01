import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by Simon on 2/25/2017.
 */
public class ZengBot extends ListenerAdapter {
    static AudioManager manager = null;
    static final String id = "286283344784916480";

    private static AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private static Map<Long, GuildMusicManager> musicManagers = new HashMap<>();

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
            AudioSourceManagers.registerRemoteSources(playerManager);
            AudioSourceManagers.registerLocalSource(playerManager);
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
            String output = "`[To: " + author.getName() + "]` ";
            Guild guild = event.getGuild();
            VoiceChannel vChannel;
            String[] msgArr = msg.split(" ");
            switch(msgArr[0]) {
                case "-debug":
                    output += debugOutput;
                    break;
                case "-help":
                    output += "Available commands: -debug, -help, -join, -leave, -play, -skip";
                    break;
                case "-join":
                    vChannel = getUserCurrentVoiceChannel(author, guild);
                    output += "Joining `[" + vChannel.getName() + "]`";
                    guild.getAudioManager().openAudioConnection(vChannel);
                    break;
                case "-leave":
                    try {
                        vChannel = getUserCurrentVoiceChannel(guild.getMemberById(id).getUser(), guild);
                        guild.getAudioManager().closeAudioConnection();
                        output += "Left `[" + vChannel.getName() + "]`";
                    } catch (NullPointerException e) {
                        output += "Not in a voice channel right now!";
                    }
                    break;
                case "-play":
                    try {
                        vChannel = getUserCurrentVoiceChannel(guild.getMemberById(id).getUser(), guild);
                        if (msgArr.length == 2)
                            loadAndPlay(event.getTextChannel(), msgArr[1]);
                    } catch (Exception e) {
                        output += "Not in a voice channel.";
                    }
                    break;
                case "-skip":
                    skipTrack(event.getTextChannel());
                    break;
                default:
                    output += "Unknown command.";
            }
            channel.sendMessage(output).queue();
            message.deleteMessage().queue();
        }
    }


    public VoiceChannel getUserCurrentVoiceChannel(User user, Guild guild) {
        for (VoiceChannel chn : guild.getVoiceChannels()) {
            for (Member memberInChannel : chn.getMembers()) {
                if (user.getId().equals(memberInChannel.getUser().getId())) {
                    return chn;
                }
            }
        }
        return null;
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("`[Music]` Adding to queue " + track.getInfo().title).queue();

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("`[Music]` Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("`[Music]` Nothing found by " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("`[Music]` Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("`[Music]` Skipped to next track.").queue();
    }
}

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by Simon on 2/25/2017.
 */
public class ZengBot extends ListenerAdapter {
    //todo: fix -# returning things when not selecting
    static AudioManager manager = null;
    static final String id = "286283344784916480"; //id of self - replace this!

    private static AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private static Map<Long, GuildMusicManager> musicManagers = new HashMap<>();
    private static Map<Long, Searcher> searchers = new HashMap<>();

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
                "Author: " + author.getName()
                + "\nAuthor ID: " + author.getId()
                + "\nMessage: " + msg
                + "\nAuthor is official bot: " + bot
                + "\nResponse number: " + responseNumber
                + "\nChannel: " + channel.getName() + "\n";
        System.out.println(debugOutput);

        if (msg.startsWith("-")) {
            message.deleteMessage().queue();
            String output = "`[To: " + author.getName() + "]` ";
            Guild guild = event.getGuild();
            VoiceChannel vChannel;
            String[] msgArr = msg.split(" ");
            switch(msgArr[0]) {
                case "-debug":
                    output += debugOutput;
                    break;
                case "-help":
                    output += "Available commands: -debug, -help, -join, -leave, -play, -skip, -queue, -waffle, -weather, -dank";
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
                        output += "Not in a voice channel right now, or something done goofed!";
                    }
                    break;
                case "-play":
                    try {
                        vChannel = getUserCurrentVoiceChannel(guild.getMemberById(id).getUser(), guild);
                        if (!vChannel.equals(null)) {
                            loadAndPlay(event.getTextChannel(), msg.substring(6));
                            output = "";
                        } else {
                            output += "Not in a voice channel, or you done goofed in command usage.";
                        }
                    } catch (Exception e) {
                        output += "Not in a voice channel, or you done goofed in command usage.";
                    }
                    break;
                case "-skip":
                    try {
                        vChannel = getUserCurrentVoiceChannel(guild.getMemberById(id).getUser(), guild);
                        if (!vChannel.equals(null)) {
                            skipTrack(event.getTextChannel());
                            output = "";
                        } else {
                            output += "Not in a voice channel.";
                        }
                    } catch (Exception e) {
                    }
                    break;
                case "-queue":
                    GuildMusicManager musicManager = getGuildAudioPlayer(guild);
                    output = "`[Music]`" + musicManager.scheduler.queueString();
                    break;
                case "-1":
                case "-2":
                case "-3":
                case "-4":
                case "-5":
                    if (getGuildSearcher(guild).isSelecting) {
                        try {
                            vChannel = getUserCurrentVoiceChannel(guild.getMemberById(id).getUser(), guild);
                            if (!vChannel.equals(null)) {
                                loadAndPlay(event.getTextChannel(), getGuildSearcher(guild).results[Integer.parseInt(msg.substring(1)) - 1]);
                                getGuildSearcher(guild).isSelecting = false;
                                output = "";
                            } else {
                                output += "Not in a voice channel.";
                            }
                        } catch (Exception e) {
                            output += "Not in a voice channel, or something done goofed.";
                        }
                    }
                    break;
                case "-c":
                    if (getGuildSearcher(guild).isSelecting) output += "Selection cancelled.";
                    getGuildSearcher(guild).isSelecting = false;
                    break;
                case "-waffle":
                    output += "http://www.ihop.com/-/media/DineEquity/IHop/Images/Menu/MenuItems/Belgian-Waffles/belgium_waff.ashx";
                    break;
                case "-weather":
                    output += "https://metarweather.tk/";
                    break;
                case "-dank":
                    String recat = msg.substring(6);
                    recat = recat.replaceAll("b|B", ":b:");
                    recat = recat.replaceAll("o|O", ":o2:");
                    recat = recat.replaceAll("a|A", ":a:");
                    System.out.println(recat);
                    output += recat;
                    break;
                default:
                    output += "Unknown command.";
            }
            if (!output.equals(""))
                channel.sendMessage(output).queue();
        } else if (author.getId().equals("286268809369616395") && msg.contains("won the brawl!")) {
            channel.sendMessage("!brawl").queue();
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

    private synchronized Searcher getGuildSearcher(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        Searcher searcher = searchers.get(guildId);

        if (searcher == null) {
            searcher = new Searcher();
            searchers.put(guildId, searcher);
        }

        return searcher;
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        Searcher searcher = getGuildSearcher(channel.getGuild());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("`[Music]` Adding to queue " + track.getInfo().title).queue();

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                //todo: automatically add every thing in the playlist to queue
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("`[Music]` Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                //add in search here
//                channel.sendMessage("`[Music]` Nothing found by " + trackUrl).queue();
                ArrayList<Map<String, String>> responses = searcher.search(trackUrl);
                String message = "`[Music]` Search results. Type -# to select a result, or -c to cancel:";
                for (int i = 0; i < 5; i++) {
                    Map<String, String> results = responses.get(i);
                    message += "\n" + (i + 1) + ". **" + results.get("title") + "** by *" + results.get("channel") + "*";
                }
                searcher.isSelecting = true;
                channel.sendMessage(message).queue();
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
        //Todo: clear queue
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();
        channel.sendMessage("`[Music]` Skipped to next track.").queue();
    }
}

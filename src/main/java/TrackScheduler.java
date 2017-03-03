import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.text.DecimalFormat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class schedules tracks for the audio player. It contains the queue of tracks.
 */
public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;

    /**
     * @param player The audio player this scheduler uses
     */
    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
    }

    /**
     * Add the next track to queue or play right away if nothing is in the queue.
     *
     * @param track The track to play or add to queue.
     */
    public void queue(AudioTrack track) {
        // Calling startTrack with the noInterrupt set to true will start the track only if nothing is currently playing. If
        // something is playing, it returns false and does nothing. In that case the player was already playing so this
        // track goes to the queue instead.
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    /**
     * Start the next track, stopping the current one if it is playing.
     */
    public void nextTrack() {
        // Start the next track, regardless of if something is already playing or not. In case queue was empty, we are
        // giving null to startTrack, which is a valid argument and will simply stop the player.
        player.startTrack(queue.poll(), false);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Only start the next track if the end reason is suitable for it (FINISHED or LOAD_FAILED)
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }

    public String queueString() {
        String output = "";
        int counter = 1;
        AudioTrack current = player.getPlayingTrack();
        DecimalFormat format = new DecimalFormat("00");
        long total = 0;
        try {
            if (!current.equals(null)) {
                long position = current.getPosition();
                total += current.getDuration() - position;
                String timeStamp = "** [" + position/60000 + ":" + format.format((position%60000)/1000) + "/" + current.getDuration()/60000 + ":" + format.format((current.getDuration()%60000)/1000) + "]";
                output = "\nCurrently playing: **" + current.getInfo().title + timeStamp;
            }
        } catch (Exception e) {
        }
        for (AudioTrack t: queue) {
            AudioTrackInfo info = t.getInfo();
            total += info.length;
            output += "\n" + counter++ + ". **" + info.title + "** [" + info.length/60000 + ":" + format.format((info.length%60000)/1000) + "]";
        }
        output += "\nTotal queued length: [" + total/60000 + ":" + format.format((total%60000)/1000) + "]";
        return output;
    }
}
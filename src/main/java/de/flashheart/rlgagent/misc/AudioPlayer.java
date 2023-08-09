package de.flashheart.rlgagent.misc;

import jdk.nashorn.internal.runtime.logging.Logger;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

/**
 * this is a wrapper class to play music files via mpg321. It seemed much easier to use this trusted player rather than
 * some complicated java sound libraries.
 */
@Log4j2
public class AudioPlayer {
    private final Configs configs;
    private File mpg_bin;
    public static final String MUSIC = "music";
    public static final String VOICE1 = "voice1";
    public static final String VOICE2 = "voice2";
    public static final String SOUND1 = "sound1";
    public static final String SOUND2 = "sound2";
    private HashMap<String, Process> process_map;

    public AudioPlayer(Configs configs) {
        this.configs = configs;
        process_map = new HashMap<>();
        mpg_bin = new File(configs.get(Configs.MPG321_BIN));
    }

    public void stop(String channel) {
        if (process_map.containsKey(channel))
            process_map.remove(channel).destroy();
    }

    public void play(final String subpath, final String songfile) {
        play(MUSIC, subpath, songfile);
    }

    /**
     * @param songfile the file to be played. if not existent, then nothing happens. if "<random>" then a random file is
     *                 picked.
     */
    public void play(final String channel, final String subpath, final String songfile) {
        if (!mpg_bin.exists()) return;
        Optional<File> file = Optional.empty();
        if (songfile.equals("<random>"))
            file = configs.pickRandomAudioFile(subpath);
        else
            file = configs.getAudioFile(subpath, songfile);

        stop(channel);

        file.ifPresent(file1 -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                String cmd = String.format("%s %s %s", configs.get(Configs.MPG321_BIN), configs.get(Configs.MPG321_OPTIONS), "\"" + file1.getAbsoluteFile() + "\"");
                processBuilder.command("bash", "-c", cmd);
                process_map.put(channel, processBuilder.start());
            } catch (IOException e) {
                log.warn(e);
            }
        });
    }

}

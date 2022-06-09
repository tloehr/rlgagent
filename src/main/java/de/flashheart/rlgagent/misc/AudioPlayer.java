package de.flashheart.rlgagent.misc;

import jdk.nashorn.internal.runtime.logging.Logger;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * this is a wrapper class to play music files via mpg321. It seemed much easier to use this trusted player rather than
 * some complicated java sound libraries.
 */
@Log4j2
public class AudioPlayer {
    private final Configs configs;
    private Optional<Process> process;
    private File mpg_bin;

    public AudioPlayer(Configs configs) {
        this.configs = configs;
        process = Optional.empty();
        mpg_bin = new File(configs.get(Configs.MPG321_BIN));
    }

    public void stop() {
        process.ifPresent(process1 -> process1.destroy());
        process = Optional.empty();
    }

    /**
     * @param songfile the file to be played. if not existent, then nothing happens. if "<random>" then a random file is
     *                 picked.
     */
    public void play(String subpath, String songfile) {
        if (!mpg_bin.exists()) return;
        Optional<File> file = Optional.empty();
        if (songfile.equals("<random>"))
            file = configs.pickRandomAudioFile(subpath);
        else
            file = configs.getAudioFile(subpath, songfile);
        
        stop();

        file.ifPresent(file1 -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                String cmd = String.format("%s %s", configs.get(Configs.MPG321_BIN), "\"" + file1.getAbsoluteFile() + "\"");
                processBuilder.command("bash", "-c", cmd);
                process = Optional.of(processBuilder.start());
            } catch (IOException e) {
                log.warn(e);
            }
        });
    }

}

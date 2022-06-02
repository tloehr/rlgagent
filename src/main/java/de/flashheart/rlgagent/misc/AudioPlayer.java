package de.flashheart.rlgagent.misc;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * this is a wrapper class to play music files via mpg321. It seemed much easier to use this trusted player rather than
 * some complicated java sound libraries.
 */
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

    public void play(String songfile) throws IOException {
        if (!mpg_bin.exists()) return;
        stop();
        Optional<File> file = configs.getAudioFile(songfile);
        if (file.isPresent()) {
            ProcessBuilder processBuilder = new ProcessBuilder();
            String cmd = String.format("%s %s", configs.get(Configs.MPG321_BIN), "\"" + file.get().getAbsoluteFile() + "\"");
            processBuilder.command("bash", "-c", cmd);
            process = Optional.of(processBuilder.start());
        }
    }

}

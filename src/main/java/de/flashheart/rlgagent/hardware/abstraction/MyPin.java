package de.flashheart.rlgagent.hardware.abstraction;

import com.pi4j.gpio.extension.mcp.MCP23017GpioProvider;
import com.pi4j.gpio.extension.mcp.MCP23017Pin;
import com.pi4j.io.gpio.*;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.extern.log4j.Log4j2;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
import java.util.Arrays;
import java.util.Optional;

/**
 * Created by tloehr on 07.06.15.
 */
@Log4j2
public class MyPin {
    private final Optional<GpioPinDigitalOutput> outputPin;
    private final String name;
    private final Optional<MyUI> myUI;
    private int note = -1;
    private Synthesizer synthesizer = null;
    private MidiChannel[] channels;

    public MyPin(String name, Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, Optional<MCP23017GpioProvider> gpioProvider, int instrument, int note) {
        this.name = name;
        this.myUI = myUI;
        this.note = note;

        if (gpio.isPresent()) {
            Optional<Pin> raspiPin = Optional.ofNullable(RaspiPin.getPinByName(configs.get(name)));
            Optional<Pin> mcpPin = Arrays.stream(MCP23017Pin.ALL).sequential().filter(pin1 -> pin1.getName().equalsIgnoreCase(configs.get(name))).findFirst();

            if (raspiPin.isPresent()) {
                outputPin = Optional.of(gpio.get().provisionDigitalOutputPin(raspiPin.get()));
                outputPin.get().setState(PinState.LOW);
            } else if (mcpPin.isPresent() && gpioProvider.isPresent()) {
                outputPin = Optional.of(gpio.get().provisionDigitalOutputPin(gpioProvider.get(), mcpPin.get(), PinState.LOW));
            } else {
                outputPin = Optional.empty();
            }

        } else {
            outputPin = Optional.empty();
            // Die Tonerzeugung ist nur zum Testen auf normalen Rechnern
            // und nicht auf dem RASPI. Da muss keine Rechenzeit verschwendet werden.
            try {
                if (instrument > 0) {
                    synthesizer = MidiSystem.getSynthesizer();
                    synthesizer.open();
                    channels = synthesizer.getChannels();
                    channels[0].programChange(instrument);
                    synthesizer.loadAllInstruments(synthesizer.getDefaultSoundbank());
                }
            } catch (javax.sound.midi.MidiUnavailableException e) {
                synthesizer = null;
            }
        }

        log.debug("adding MyPin " + name);

    }

    public String getName() {
        return name;
    }

    public void setState(boolean on) {
        outputPin.ifPresent(gpioPinDigitalOutput -> gpioPinDigitalOutput.setState(on ? PinState.HIGH : PinState.LOW));
        log.trace(getName() + ": "+ (on ? "ON" : "off"));
        myUI.ifPresent(myUI1 -> myUI1.setState(name, on));

        if (synthesizer != null) {
            if (on) channels[0].noteOn(note, 90);
            else channels[0].noteOff(note);
        }
    }
}

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
    private final int note;
    private Synthesizer synthesizer = null;
    private MidiChannel[] channels;
    private final boolean trigger_on_high;
    private boolean on = false;


    public MyPin(String name, Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, Optional<MCP23017GpioProvider> gpioProvider, int instrument, int note) {
        this(name, configs, myUI, gpio, gpioProvider, instrument, note, true);
    }

    /**
     * @param name
     * @param configs
     * @param myUI
     * @param gpio
     * @param gpioProvider    only valid when running on a Raspberry
     * @param instrument      MIDI instrument to simulate a siren signal in the GUI version
     * @param note            to simulate a siren signal in the GUI version
     * @param trigger_on_high true (which is default) means, that on will set the pin to HIGH. false, means ON sets the
     *                        pin to LOW. Mainly necessary for relais boards, which come in two flavors.
     */
    public MyPin(String name, Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, Optional<MCP23017GpioProvider> gpioProvider, int instrument, int note, boolean trigger_on_high) {
        this.name = name;
        this.myUI = myUI;
        this.note = note;
        this.trigger_on_high = trigger_on_high;

        if (gpio.isPresent()) {
            Optional<Pin> raspiPin = Optional.ofNullable(RaspiPin.getPinByName(configs.get(name)));
            Optional<Pin> mcpPin = Arrays.stream(MCP23017Pin.ALL).sequential().filter(pin1 -> pin1.getName().equalsIgnoreCase(configs.get(name))).findFirst();

            // if the connected device expects the pin state "reversed". like many relay boards.
            final PinState init_state = trigger_on_high ? PinState.LOW : PinState.HIGH;

            if (raspiPin.isPresent()) {
                outputPin = Optional.of(gpio.get().provisionDigitalOutputPin(raspiPin.get()));
                outputPin.get().setState(init_state);
            } else if (mcpPin.isPresent() && gpioProvider.isPresent()) {
                outputPin = Optional.of(gpio.get().provisionDigitalOutputPin(gpioProvider.get(), mcpPin.get(), init_state));
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
        if (this.on == on) return;
        this.on = on;
        // trigger_on_high can invert the PinState / only for hardware not for UI
        // simplified trigger_on_high ? on : !on to trigger_on_high == on
        outputPin.ifPresent(gpioPinDigitalOutput -> gpioPinDigitalOutput.setState(trigger_on_high == on ? PinState.HIGH : PinState.LOW));
        log.trace("{}: trigger_on_high {} => {}", trigger_on_high, getName(), (on ? "ON" : "off"));

        myUI.ifPresent(myUI1 -> myUI1.setState(name, on));

        if (synthesizer != null) {
            if (on) channels[0].noteOn(note, 90);
            else channels[0].noteOff(note);
        }
    }

    public boolean isOn(){
        return on;
    }
}

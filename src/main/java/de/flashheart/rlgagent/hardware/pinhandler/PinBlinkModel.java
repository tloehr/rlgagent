package de.flashheart.rlgagent.hardware.pinhandler;

import de.flashheart.rlgagent.hardware.abstraction.MyPin;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.ArrayList;


/**
 * Created by tloehr on 14.07.16.
 */
@Log4j2
public class PinBlinkModel implements GenericBlinkModel {
    public static final String SCHEME_TEST_REGEX = "^(\\d+|∞):(((on|off){1},\\d+)+(;((on|off){1},\\d+))*)$";


    MyPin pin;
    private ArrayList<PinScheduleEvent> onOffScheme;
    int repeat;

    String infinity = "\u221E";

    public PinBlinkModel(MyPin pin) {

        this.onOffScheme = new ArrayList<>();
        this.pin = pin;
        this.repeat = Integer.MAX_VALUE;
    }

    @Override
    public String call() {
        log.trace(LocalDateTime.now() + " working on:" + pin.getName() + "]  onOffScheme.size()=" + onOffScheme.size());

        if (repeat == 0) {
            pin.setState(false);
            return null;
        }

        for (int turn = 0; turn < repeat; turn++) {

            for (PinScheduleEvent event : onOffScheme) {

                if (Thread.currentThread().isInterrupted()) {
                    pin.setState(false);
                    return null;
                }

                pin.setState(event.isOn());

                try {
                    Thread.sleep(event.getDuration());
                } catch (InterruptedException exc) {
                    pin.setState(false);
                    return null;
                }

            }
        }

        return null;
    }



    /**
     * accepts a blinking scheme as a String formed like this: "repeat:[on|off],timeINms;*".
     * if repeat is 0 then a previous blinking process is stopped and the pin is set to OFF.
     * There is no "BLINK FOREVER" really. But You could always put Integer.MAX_VALUE as REPEAT instead into the String.
     *
     * @param scheme
     */
    @Override
    public void setScheme(String scheme) {

        onOffScheme.clear();

        log.trace("new scheme for pin: " + pin.getName() + " : " + scheme);

        // zuerst wiederholungen vom muster trennen
        String[] splitFirstTurn = scheme.trim().split(":");
        String repeatString = splitFirstTurn[0];
        repeat = repeatString.equals("∞") ? Integer.MAX_VALUE : Integer.parseInt(repeatString);

        if (repeat > 0) {
            // Hier trennen wir die einzelnen muster voneinander
            String[] splitSecondTurn = splitFirstTurn[1].trim().split(";");

            for (String pattern : splitSecondTurn) {
                String[] splitThirdTurn = pattern.trim().split(",");
                onOffScheme.add(new PinScheduleEvent(splitThirdTurn[0], splitThirdTurn[1]));
            }
        }

        log.trace(scheme);
    }

}

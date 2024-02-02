package de.flashheart.rlgagent.hardware;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;

import java.util.LinkedList;

@ToString
@Log4j2
public class PinScheme {
    MyPin myPin;
    int repeat;
    int pointer;
    LinkedList<Integer> scheme; // what the pin is currently working on

    public PinScheme(MyPin myPin) {
        this.myPin = myPin;
        repeat = 0;
        pointer = 0;
        scheme = new LinkedList<>();
    }

    void clear() {
        log.trace("clearing scheme for pin {}", myPin.getName());
        scheme.clear();
        repeat = 0;
        pointer = 0;
        myPin.setState(false);
    }

    /**
     * creates a scheme but chops the time value into pieces of 25ms (PERIOD)
     *
     * @param repeat
     * @param json_scheme
     */
    void init(int repeat, JSONArray json_scheme) {
        clear();
        this.repeat = repeat;
        json_scheme.forEach(o -> {
            int value = Integer.parseInt(o.toString());
            int multiplier = Math.abs(value / PinHandler.PERIOD);
            int sign = value >= 0 ? 1 : -1;

            // add <multiplier>-times the value to the list
            for (int i = 0; i < multiplier; i++)
                scheme.add(PinHandler.PERIOD * sign);
        });
    }

    void next() {
        if (scheme.isEmpty()) return;

        myPin.setState(scheme.get(pointer) >= 0);
        pointer++;
        if (pointer >= scheme.size()) { // its over
            pointer = 0;
            if (repeat > 0) { // we need to repeat at least once more
                log.trace("list is empty - refilling for repeat {} ", repeat);
                repeat--;
            } else clear();
        }
    }
}

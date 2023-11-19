package de.flashheart.rlgagent.hardware.pinhandler;

import de.flashheart.rlgagent.hardware.abstraction.MyPin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import java.util.LinkedList;

@Getter
@Setter
@ToString
@Log4j2
public class PinScheme {
    MyPin myPin;
    int repeat;
    LinkedList<Integer> scheme; // what the pin is currently working on
    LinkedList<Integer> template; // the original scheme to reuse, until repeat reaches 0;

    public PinScheme(MyPin myPin) {
        this.myPin = myPin;
        repeat = 0;
        scheme = new LinkedList<>();
        template = new LinkedList<>();
    }


    boolean is_empty(){
        return scheme.isEmpty();
    }

    int pop(){
        return scheme.remove();
    }

    void reset_scheme() {
        scheme = new LinkedList<>(template);
        repeat--;
    }

    void init_template() {
        template = new LinkedList<>(scheme);
    }

    void clear(){
        log.debug("clearing scheme for pin {}", myPin.getName());
        scheme.clear();
        template.clear();
        repeat = 0;
        myPin.setState(false);
    }
}

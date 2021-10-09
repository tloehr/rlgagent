package de.flashheart.rlgagent.hardware.pinhandler;

import java.util.concurrent.Callable;

public interface GenericBlinkModel extends Callable<String> {
    void setScheme(String scheme);




}

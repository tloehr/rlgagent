package de.flashheart.rlgagent.hardware.pinhandler;


import de.flashheart.rlgagent.hardware.abstraction.MyPin;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This handler runs parallel to the main programm and handles all the blinking needs of the specific pins. It knows to handle collision between pins that must not be run at the same time.
 * Dieser Handler läuft parallel zum Hauptprogramm. Er steuert alles Relais und achtet auch auf widersprüchliche Befehle und Kollisionen (falls bestimmte Relais nicht gleichzeitig anziehen dürfen, gibt mittlerweile nicht mehr).
 */
@Log4j2
public class PinHandler {

    final ReentrantLock lock;
    final HashMap<String, GenericBlinkModel> pinMap;
    final HashMap<String, Future<String>> futures;
    private ExecutorService executorService;

    public PinHandler() {
        lock = new ReentrantLock();
        pinMap = new HashMap<>();
        futures = new HashMap<>();
        executorService = Executors.newFixedThreadPool(20);
    }

    /**
     * adds a a relay to the handler.
     *
     * @param myPin der betreffende Pin
     */
    public void add(MyPin myPin) {
        lock.lock();
        try {
            pinMap.put(myPin.getName(), new PinBlinkModel(myPin));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Setzt ein Blink Schema für diesen Pin. Die Syntax ist wie folgt:
     * "<anzahl_wiederholung/>;(millis-on;millis-off)*", wobei ()* bedeutet, dass diese Sequenz so oft wie
     * gewünscht wiederholt werden kann. Danach wird die Gesamtheit <anzahl_wiederholungen/> mal wiederholt
     * wird. Unendliche Wiederholungen werden einfach durch Long.MAX_VALUE
     *
     * @param name
     * @param scheme
     */
    public void setScheme(String name, String scheme) {
        if (lock.isLocked()) log.warn("setScheme() is currently locked. Delay will occur. Why is this happening ?");
        if (scheme.equalsIgnoreCase("off")) scheme = "0:";
        lock.lock();
        try {
            GenericBlinkModel genericBlinkModel = pinMap.get(name);
            if (genericBlinkModel != null) {
                if (futures.containsKey(name) && !futures.get(name).isDone()) { // but only if it runs
                    futures.get(name).cancel(true);
                }
                genericBlinkModel.setScheme(scheme);
                futures.put(name, executorService.submit(genericBlinkModel));
            } else {
                log.error("Element not found in handler");
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.fatal(e);
            System.exit(0);
        } finally {
            lock.unlock();
        }
    }

    public void off(String name) {
        setScheme(name, "0:");
    }

    public void off() {
        for (String name : pinMap.keySet()) {
            off(name);
        }
    }


}

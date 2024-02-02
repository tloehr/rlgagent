package de.flashheart.rlgagent.hardware;

import de.flashheart.rlgagent.misc.Configs;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
public class PinHandler implements Runnable {
    public static final int PERIOD = 25;
    private final Configs configs;
    ScheduledExecutorService executor;
    ReentrantLock lock;

    // the registry has a Quartet with the Pin, the repeat number, the active scheme and the backup scheme
    HashMap<String, PinScheme> pin_registry;
    //List<String> keys = Arrays.asList("wht", "red", "ylw", "grn", "blu", "sir1", "sir2", "sir3", "sir4", "buzzer");
    // contains a repeat number of every Linkes List available

    public PinHandler(Configs configs) {
        this.configs = configs;
        pin_registry = new HashMap<>();
        lock = new ReentrantLock();
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this, 0, PERIOD, TimeUnit.MILLISECONDS);
    }

    public void add(MyPin myPin) {
        lock.lock();
        try {
            pin_registry.put(myPin.getName(), new PinScheme(myPin));
        } finally {
            lock.unlock();
        }
    }

    public void parse_incoming(final JSONObject incoming) {

        lock.lock();

        try {
            // Preprocess sir_all and led_all device selectors
            // off is processed first, and then removed from the message
            // other schemes are duplicated to their devices names.
            if (incoming.has("sir_all")) {
                if (incoming.optString("sir_all").equalsIgnoreCase("off")) {
                    Arrays.asList(Configs.ALL_SIRENS).forEach(this::off);
                } else {
                    Arrays.asList(Configs.ALL_SIRENS).forEach(key -> {
                        incoming.remove(key);
                        incoming.put(key, incoming.get("sir_all"));
                    });
                }
            }
            incoming.remove("sir_all");

            if (incoming.has("led_all")) {
                if (incoming.optString("led_all").equalsIgnoreCase("off")) {
                    Arrays.asList(Configs.ALL_LEDS).forEach(this::off);
                } else {
                    Arrays.asList(Configs.ALL_LEDS).forEach(key -> {
                        incoming.remove(key);
                        incoming.put(key, incoming.get("led_all"));
                    });
                }
            }
            incoming.remove("led_all");

            incoming.keySet().forEach(key -> {
                log.trace("{} found", key);

                if (incoming.get(key).toString().equalsIgnoreCase("off")) {
                    off(key);
                    return;
                }

                JSONObject json_scheme;
                if (configs.getScheme_macros().keySet().contains(incoming.optString(key, "NOT_A_STRING"))) {
                    // replace scheme with macro
                    json_scheme = configs.getScheme_macros().getJSONObject(incoming.getString(key));
                } else {
                    json_scheme = incoming.getJSONObject(key);
                }

                int repeat = json_scheme.getInt("repeat") < 0 ? Integer.MAX_VALUE : json_scheme.getInt("repeat") - 1;
                pin_registry.get(key).init(repeat, json_scheme.getJSONArray("scheme"));

            });
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void run() {
        lock.lock();
        try {
            pin_registry.values().forEach(PinScheme::next);
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
            // empty list, nothing to do
        } finally {
            lock.unlock();
        }
    }

    public void off(String key) {
        lock.lock();
        try {
            pin_registry.get(key).clear();
        } finally {
            lock.unlock();
        }
    }

    public void leds_off() {
        lock.lock();
        try {
            Arrays.asList(Configs.ALL_LEDS).forEach(this::off);
            //pin_registry.values().forEach(PinScheme::clear);
        } finally {
            lock.unlock();
        }
    }

    public void off() {
        lock.lock();
        try {
            pin_registry.values().forEach(PinScheme::clear);
        } finally {
            lock.unlock();
        }
    }


}

package de.flashheart.rlgagent.hardware.pinhandler;


import de.flashheart.rlgagent.hardware.abstraction.MyPin;
import de.flashheart.rlgagent.misc.Configs;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;


import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;


@Log4j2
public class PinHandler2 implements Runnable {


    ScheduledExecutorService executor;
    LinkedList<Integer>[] schemes;
    ReentrantLock lock;
    final int period;
    int iter = 0;
    ArrayList<MyPin> pins;
    int[] repeat;
    boolean[] starts_with_ON;
    private final Configs configs;
    JSONObject scheme_backup;

    List<String> all_pins;

    public PinHandler2(Configs configs, int period) {
        this.period = period;
        this.scheme_backup = new JSONObject();

        all_pins = new ArrayList<>(Arrays.asList(Configs.ALL_PINS));

        this.configs = configs;
        schemes = new LinkedList[]{
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>(),
                new LinkedList<Integer>()
        };
        lock = new ReentrantLock();
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this, 0, period, TimeUnit.MILLISECONDS);
    }



    public void setScheme(String pin_name, String scheme) {
        if (lock.isLocked()) log.warn("setScheme() is currently locked. Delay will occur.");
        if (scheme.equalsIgnoreCase("off")) scheme = "0:";
        scheme = configs.getScheme(scheme); // replace with predefines scheme or leave it as it was

        lock.lock();


        try {


        } finally {
            lock.unlock();
        }
    }



    private void parse_incoming(final JSONObject incoming) {
        lock.lock();
        try {
            incoming.keys().forEachRemaining(key -> {
                JSONObject my_incoming = incoming.getJSONObject(key);
                scheme_backup.put(key, my_incoming);
                parse_incoming(my_incoming, all_pins.indexOf(key));
            });

        } finally {
            lock.unlock();
        }
    }

    private void parse_incoming(JSONObject my_incoming, int key_index) {
        String str_repeat = my_incoming.getString("repeat");
        repeat[key_index] = (str_repeat.equalsIgnoreCase("forever") ? Integer.MAX_VALUE : Integer.parseInt(str_repeat)) - 1;

//        grmof
//        String scheme_tag = my_incom

        JSONArray my_scheme = my_incoming.getJSONArray("scheme");
        final LinkedList<Integer> my_linked_list = new LinkedList<>();
        my_scheme.forEach(o -> my_linked_list.add(Integer.parseInt(o.toString())));
        schemes[key_index] = my_linked_list;
    }


    public void off(String name) {
        setScheme(name, "0:");
    }

    /**
     * set all known pins to off
     */
    public void off() {
        Arrays.asList(Configs.ALL_PINS).forEach(this::off);
    }


    @Override
    public void run() {
        lock.lock();
        try {
            // check all pins and set their on/off state
            all_pins.forEach(key -> {
                int k = all_pins.indexOf(key);
                LinkedList<Integer> scheme = schemes[k];
                if (scheme.isEmpty()) return;

                if (scheme.get(0) <= 0) {
                    scheme.remove(); // head
                    log.debug("removed head. NEW list {} : {}", key, scheme);
                    // REPEAT functionality
                    if (scheme.isEmpty() && repeat[k] > 0) { // last pop emptied the list but we need to repeat at least once more
                        int saved_repeat = repeat[k] - 1;
                        //parse_incoming(key); // recreate initial list for this key
                        repeat[k] = saved_repeat;
                    }
                }

                // if list size is EVEN => then head must be ON, if ODD => means OFF
                boolean state = scheme.size() % 2 == 0;
                pins.get(k).setState(state);
                // no subtract the period time from the head of the list
                scheme.set(0, scheme.get(0) - period);
                log.debug("changed list to {} : {}", key, scheme);
            });
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
            // empty list, nothing to do
        } finally {
            lock.unlock();
        }
    }
}

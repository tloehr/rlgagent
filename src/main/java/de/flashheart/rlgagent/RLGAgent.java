package de.flashheart.rlgagent;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import de.flashheart.rlgagent.hardware.Agent;
import de.flashheart.rlgagent.hardware.abstraction.MyLCD;
import de.flashheart.rlgagent.hardware.pinhandler.PinHandler;
import de.flashheart.rlgagent.jobs.NetworkMonitoringJob;
import de.flashheart.rlgagent.jobs.StatusJob;
import de.flashheart.rlgagent.misc.AudioPlayer;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.JavaTimeConverter;
import de.flashheart.rlgagent.misc.Tools;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Log4j2
public class RLGAgent implements MqttCallbackExtended, PropertyChangeListener {
    private final String EVENTS, CMD4ME;//, CMD4ALL;
    private final Optional<MyUI> myUI;
    private final Optional<GpioController> gpio;
    private final PinHandler pinHandler;
    private final Configs configs;
    private final MyLCD myLCD;
    private Optional<IMqttClient> iMqttClient;
    private List<String> potential_brokers;

    private final Scheduler scheduler;
    private final Agent me;
    private SimpleTrigger networkMonitoringTrigger, statusTrigger;
    private final JobKey networkMonitoringJob, statusJob;
    private long mqtt_connect_tries = 0L;
    private long netmonitor_cycle = 0L;
    private long failed_pings_with_mqtt_connection = 0L;
    private String active_broker = "";
    private HashMap<String, String> current_network_stats;
    public static final DateTimeFormatter myformat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM);
    private AudioPlayer audioPlayer;
    private Optional<String> progress_timer;
    // Pair(Timerkey, led_grn, last_change as millis in long)
    private Optional<Pair<String, String>> blinking_timer;

    private String[] PROGRESS_SCHEMES;
    private int prev_progress_timer = -1;

    public RLGAgent(Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, PinHandler pinHandler, MyLCD myLCD) throws SchedulerException {
        //this.lock = new ReentrantLock();

        log.info("RLG-Agent {}b{} {}", configs.getBuildProperties("my.version"), configs.getBuildProperties("buildNumber"), configs.getBuildProperties("buildDate"));

        progress_timer = Optional.empty();
        blinking_timer = Optional.empty();
        this.myUI = myUI;
        this.gpio = gpio;
        this.pinHandler = pinHandler;
        this.configs = configs;
        this.myLCD = myLCD;
        this.myLCD.addPropertyChangeListener(this);
        PROGRESS_SCHEMES = new String[]{
                configs.get_blink_schemes().getProperty("normal"),
                configs.get_blink_schemes().getProperty("normal"),
                configs.get_blink_schemes().getProperty("fast"),
                configs.get_blink_schemes().getProperty("fast"),
                configs.get_blink_schemes().getProperty("very_fast"),
        };


        potential_brokers = Arrays.asList(configs.get(Configs.MQTT_BROKER).trim().split("\\s+"));
        current_network_stats = new HashMap<>();

        me = new Agent(new JSONObject()
                .put("agentid", configs.getAgentname())
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now())));

        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler.getContext().put("rlgAgent", this);
        this.scheduler.start();

        audioPlayer = new AudioPlayer(configs);

        iMqttClient = Optional.empty();

        // inbound
        //CMD4ALL = String.format("%s/cmd/all/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());
        CMD4ME = String.format("%s/cmd/%s/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        // outbound
        EVENTS = String.format("%s/evt/%s/", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        networkMonitoringJob = new JobKey(NetworkMonitoringJob.name, "group1");
        statusJob = new JobKey(StatusJob.name, "group1");

        initAgent();
        initNetworkConnection();
        initStatusJob();
    }

    private void disconnect_from_mqtt_broker() {
        iMqttClient.ifPresent(iMqttClient1 -> {
            log.debug("disconnecting from mqtt if necessary");
            try {
                iMqttClient1.disconnect();
                log.debug("disconnecting " + iMqttClient1.getClientId());
            } catch (MqttException e) {
                log.warn(e.getMessage());
            }
            try {
                iMqttClient1.close();
                //active_broker = "";
                log.debug("closing " + iMqttClient1.getClientId());
            } catch (MqttException e) {
                log.warn(e.getMessage());
            }
        });
        iMqttClient = Optional.empty();
        log.info("disconnected from mqtt");
    }

    /**
     * belongs to connect_to_mqtt_broker()
     *
     * <ol>
     * <li> </li>
     * </ol>
     *
     * @param uri to try for connection
     * @return true when connection was successful, false otherwise
     */
    private boolean try_mqtt_broker(String uri) {
        boolean success = false;

        try {
            mqtt_connect_tries++;
            log.debug("trying broker @{} number of tries: {}", uri, mqtt_connect_tries);
            iMqttClient = Optional.of(new MqttClient(uri, String.format("%s-%s", me.getAgentid(), configs.get(Configs.MYUUID)), new MqttDefaultFilePersistence(configs.getWORKSPACE())));
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(configs.is(Configs.MQTT_RECONNECT));
            options.setCleanSession(configs.is(Configs.MQTT_CLEAN_SESSION));
            options.setConnectionTimeout(configs.getInt(Configs.MQTT_TIMEOUT));
            options.setMaxInflight(configs.getInt(Configs.MQTT_MAX_INFLIGHT));

            if (iMqttClient.isPresent()) {
                iMqttClient.get().connect(options);
                iMqttClient.get().setCallback(this);
            }

            if (mqtt_connected()) {
                log.info("Connected to the broker @{} with ID: {}", iMqttClient.get().getServerURI(), iMqttClient.get().getClientId());
                iMqttClient.get().subscribe(CMD4ME, configs.getInt(Configs.MQTT_QOS), (topic, receivedMessage) -> proc(topic, receivedMessage));
                success = true;
            }
        } catch (MqttException e) {
            iMqttClient = Optional.empty();
            log.warn(e.getMessage());
            success = false;
        }
        return success;
    }

    private void proc(String topic, MqttMessage receivedMessage) {
        List<String> tokens = Collections.list(new StringTokenizer(topic, "/")).stream().map(token -> (String) token).collect(Collectors.toList());
        if (tokens.size() < 4 || tokens.size() > 5) return;
        String cmd = tokens.get(tokens.size() - 1);

        log.debug("received {} from {} cmd {}", receivedMessage, topic, cmd);
        try {
            String payload = new String(receivedMessage.getPayload());
            final JSONObject json = new JSONObject(payload.isEmpty() ? "{}" : payload);
            // <cmd/> => paged|signals|timers|vars
            if (cmd.equalsIgnoreCase("paged")) {
                procPaged(json);
            } else if (cmd.equalsIgnoreCase("visual")) {
                procVisual(json);
            } else if (cmd.equalsIgnoreCase("acoustic")) {
                procAcoustic(json);
            } else if (cmd.equalsIgnoreCase("play")) {
                procPlay(json);
            } else if (cmd.equalsIgnoreCase("timers")) {
                procTimers(json);
            } else if (cmd.equalsIgnoreCase("vars")) {
                procVars(json);
            } else if (cmd.equalsIgnoreCase("status")) {
                scheduler.triggerJob(statusJob);
            } else if (cmd.equalsIgnoreCase("shutdown")) {
                procShutdown(true);
            } else {
                log.warn("unknown command {}", cmd);
            }
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    public void procShutdown(boolean system_shutdown) {
        log.trace("Shutdown initiated");
        myLCD.init();
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}b${agbuild}");
        myLCD.setLine("page0", 3, "Agent shutdown");
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        disconnect_from_mqtt_broker();
        configs.saveConfigs();
        pinHandler.off();
        if (system_shutdown) System.exit(0);
    }

    private void procTimers(JSONObject json) {
        prev_progress_timer = -1;
        Set<String> keys = json.keySet();
        if (keys.contains("_clearall")) {
            myLCD.clear_timers();
        } else
            json.keySet().forEach(sKey -> myLCD.setTimer(sKey, json.getLong(sKey)));
    }

    private void procVars(JSONObject json) {
        Set<String> keys = json.keySet();
        if (keys.contains("_clearall")) {
            myLCD.clear_timers();
        } else
            json.keySet().forEach(sKey -> myLCD.setVariable(sKey, json.getString(sKey)));
    }

    private void procPlay(JSONObject json) throws IOException {
        audioPlayer.play(json.getString("subpath"), json.getString("soundfile"));
    }

    private void procAcoustic(JSONObject json) {
        Set<String> keys = json.keySet();
        if (keys.contains("all")) {
            set_pins_to(Configs.ALL_SIRENS, json.getString("all"));
        }
        keys.remove("all");
        keys.stream().filter(s -> s.matches(Configs.ACOUSTICS)).forEach(signal_key -> {
            String signal = json.getString(signal_key);
            pinHandler.setScheme(signal_key, signal);
        });
    }

    private void procVisual(JSONObject json) {
        Set<String> keys = json.keySet();
        if (keys.contains("all")) {
            String value = json.getString("all");
            prev_progress_timer = -1;
            progress_timer = Optional.empty();
            blinking_timer = Optional.empty();
            if (value.startsWith("progress:")) {
                progress_timer = Optional.of(value.split(":")[1]);
            } else {
                set_pins_to(Configs.ALL_LEDS, value);
            }
        }
        keys.remove("all");
        keys.stream().filter(s -> s.matches(Configs.VISUALS)).forEach(signal_key -> {
            String signal = json.getString(signal_key);
            if (signal.startsWith("timer:")) {
                prev_progress_timer = -1;
                progress_timer = Optional.empty();
                blinking_timer = Optional.of(new ImmutablePair<>(signal_key, signal.split(":")[1]));
            } else {
                // if a new scheme for a led is set which was formerly used as a timer signal - it will be overwritten hence the blinking_timer will be set empty()
                if (blinking_timer.orElseGet(() -> new ImmutablePair<>("", "")).getKey().equalsIgnoreCase(signal_key)) {
                    prev_progress_timer = -1;
                    blinking_timer = Optional.empty();
                }
                pinHandler.setScheme(signal_key, signal);
            }
        });
    }

    private void procPaged(JSONObject json) {
        myLCD.init(); // always a fresh start. changes are rare.
        json.keySet().forEach(page -> {
            JSONArray lines = json.getJSONArray(page);
            for (int line = 1; line <= lines.length(); line++) {
                myLCD.setLine(page, line, lines.getString(line - 1));
            }
        });
    }

    private void initAgent() {
        // Hardware Buttons
        gpio.ifPresent(gpioController -> {
            GpioPinDigitalInput btn01 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN01)), PinPullResistance.PULL_UP);
            btn01.setDebounce(configs.getInt(Configs.BUTTON_DEBOUNCE));
            btn01.addListener((GpioPinListenerDigital) event -> {
                log.trace("button event: {}; State: {}; Edge: {}", "btn01", event.getState(), event.getEdge());
                if (event.getState() == PinState.HIGH)
                    reportEvent("btn01", new JSONObject().put("button", "up").toString());
                if (event.getState() == PinState.LOW)
                    reportEvent("btn01", new JSONObject().put("button", "down").toString());
            });

            GpioPinDigitalInput btn02 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN02)), PinPullResistance.PULL_UP);
            btn02.setDebounce(configs.getInt(Configs.BUTTON_DEBOUNCE));
            btn02.addListener((GpioPinListenerDigital) event -> {
                log.trace("button event: {}; State: {}; Edge: {}", "btn02", event.getState(), event.getEdge());
                if (event.getState() == PinState.HIGH)
                    reportEvent("btn02", new JSONObject().put("button", "up").toString());
                if (event.getState() == PinState.LOW)
                    reportEvent("btn02", new JSONObject().put("button", "down").toString());
            });
        });

        // Software Buttons
        myUI.ifPresent(myUI1 -> {
            myUI1.getBtn01().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    reportEvent("btn01", new JSONObject().put("button", "down").toString());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    reportEvent("btn01", new JSONObject().put("button", "up").toString());
                }
            });

            myUI1.getBtn02().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    reportEvent("btn02", new JSONObject().put("button", "down").toString());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    reportEvent("btn02", new JSONObject().put("button", "up").toString());
                }
            });
        });

        current_network_stats.put("link", "100/100");
        current_network_stats.put("signal", "-30");
    }

    private void initNetworkConnection() throws SchedulerException {
        if (scheduler.checkExists(networkMonitoringJob)) return;
        JobDetail job = newJob(NetworkMonitoringJob.class)
                .withIdentity(networkMonitoringJob)
                .build();

        networkMonitoringTrigger = newTrigger()
                .withIdentity(NetworkMonitoringJob.name + "-trigger", "group1")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(configs.getInt(Configs.NETWORKING_MONITOR_INTERVAL_IN_SECONDS))
                        .repeatForever())
                .build();
        scheduler.scheduleJob(job, networkMonitoringTrigger);
    }

    private void initStatusJob() throws SchedulerException {
        if (scheduler.checkExists(statusJob)) return;
        JobDetail job = newJob(StatusJob.class)
                .withIdentity(statusJob)
                .build();

        statusTrigger = newTrigger()
                .withIdentity(StatusJob.name + "-trigger", "group1")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(configs.getInt(Configs.STATUS_INTERVAL_IN_SECONDS))
                        .repeatForever())
                .build();
        scheduler.scheduleJob(job, statusTrigger);
    }


    private void reportEvent(String src, String payload) {
        if (mqtt_connected()) {
            try {
                MqttMessage msg = new MqttMessage();
                msg.setQos(configs.getInt(Configs.MQTT_QOS));
                msg.setRetained(configs.is(Configs.MQTT_RETAINED));
                if (!payload.isEmpty()) msg.setPayload(payload.getBytes());
                iMqttClient.get().publish(EVENTS + src, msg);
                log.info(EVENTS);
            } catch (MqttException mqe) {
                log.warn(mqe);
            }
        }
    }


    private void set_pins_to(Object[] pins, String scheme) {
        log.trace("set_pins_to {} with scheme {}", Arrays.asList(pins).toString(), scheme);
        for (Object pin : pins) {
            pinHandler.setScheme(pin.toString(), scheme);
        }
    }

    public void send_status_message() {
        if (mqtt_connected()) {
            JSONObject status = new JSONObject(current_network_stats);
            status.put("wifi", Tools.WIFI[me.getWifi()])
                    .put("version", "v" + configs.getBuildProperties("my.version") + " b" + configs.getBuildProperties("buildNumber"))
                    .put("mqtt-broker", active_broker)
                    .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                    .put("mqtt_connect_tries", mqtt_connect_tries)
                    .put("netmonitor_cycle", netmonitor_cycle);
            reportEvent("status", status.toString());
        }
    }
    
    public void network_connection() throws SchedulerException {
        if (System.getProperties().getProperty("network_connection", "new").equalsIgnoreCase("new")) {
            network_connection_new();
        } else {
            network_connection_legacy();
        }
    }

    /**
     * this method is called every NETWORKING_MONITOR_INTERVAL seconds to check if network is still good. maintains a
     * global map with necessary parameters to describe the current state of the network.
     * <ul>
     * <li>reads wifi parameters and stores them</li>
     * <li>pings the last reachable host first. if no luck, it tries out all given potential brokers from the config.txt</li>
     * <li>if the host answers the ping, and we are connected to a broker on that host -> WE ARE GOOD</li>
     * <li>if the host answers the ping, and we are NOT connected to a broker, then we try to connect to the broker on that host.</li>
     * <li>if nobody answers to our pings -> we CLOSE a potential connection to any MQTT broker we were connected to before.</li>
     * <li>When there is No MQTT Broker connection, the agent displays the connection status on the LCD and the LEDs.</li>
     * <li>every STATUS_INTERVAL_IN_NETWORKING_MONITOR_CYCLES cycles a status message is sent out the broker (if there is one)</li>
     * </ul>
     *
     * @throws SchedulerException
     */
    private void network_connection_legacy() {
        log.trace("checking network status");
        Tools.check_iwconfig(current_network_stats, Tools.getIWConfig(configs.get(Configs.WIFI_CMD_LINE)));
        me.setWifi(Tools.getWifiQuality(current_network_stats.get("signal")));
        myLCD.setVariable("wifi", Tools.WIFI[me.getWifi()]);

        String reachable_host = "";
        if (!Tools.fping(current_network_stats, active_broker, configs.getInt(Configs.PING_TRIES), configs.getInt(Configs.PING_TIMEOUT))) {
            ListIterator<String> brokers = potential_brokers.listIterator();
            while (reachable_host.isEmpty() && brokers.hasNext()) {
                String broker = brokers.next();
                reachable_host = Tools.fping(current_network_stats, broker, configs.getInt(Configs.PING_TRIES), configs.getInt(Configs.PING_TIMEOUT)) ? broker : "";
            }
        } else {
            reachable_host = active_broker;
        }

        // inform LCD about the current measurements
        current_network_stats.forEach((k, v) -> myLCD.setVariable(k, v));

        // if a broker is reachable but the MQTTClient is not yet connected. Try it.
        if (!reachable_host.isEmpty()) { // somebody answered
            if (!mqtt_connected()) { // but we don't have a mqtt connection yet
                myLCD.setLine("page0", 2, "Searching for broker");
                myLCD.setLine("page0", 3, "@" + reachable_host);
                active_broker = try_mqtt_broker(String.format("tcp://%s:%s", reachable_host, configs.get(Configs.MQTT_PORT))) ? reachable_host : "";
                // SUCCESS!!
                if (!active_broker.isEmpty()) {
                    myLCD.init();
                    myLCD.setLine("page0", 2, "MQTT connected to");
                    myLCD.setLine("page0", 3, active_broker);
                    myLCD.setLine("page0", 4, "");
                    set_pins_to(Configs.ALL_LEDS, "off");
                    pinHandler.setScheme(Configs.OUT_LED_WHITE, "netstatus"); // white is always flashing
                    pinHandler.setScheme(Configs.OUT_LED_BLUE, "netstatus"); // white is always flashing
                    netmonitor_cycle = 0L; // to inform commander right away
                }
            }
            // get a better ping statistics out of 5 pings to the current WORKING broker
            //Tools.fping(current_network_stats, active_broker, 5, 500);
        } else {
            disconnect_from_mqtt_broker();
        }

        if (!mqtt_connected()) {
            set_pins_to(Configs.ALL_LEDS, "off");
            pinHandler.setScheme(Configs.OUT_LED_WHITE, "netstatus"); // white is always flashing
            if (me.getWifi() > 0) pinHandler.setScheme(Configs.OUT_LED_RED, "netstatus");
            if (me.getWifi() > 2) pinHandler.setScheme(Configs.OUT_LED_YELLOW, "netstatus");
            if (me.getWifi() > 3) pinHandler.setScheme(Configs.OUT_LED_GREEN, "netstatus");

            if (!myLCD.pageExists("network1")) {
                myLCD.welcome_page();

                myLCD.setLine("network1", 1, "ssid:${essid}");
                myLCD.setLine("network1", 2, "AP: ${ap}");
                myLCD.setLine("network1", 3, "signal:${signal}");
                myLCD.setLine("network1", 4, "wifi:${wifi}");

                myLCD.setLine("network2", 1, "${ping_host} ${ping_success}");
                myLCD.setLine("network2", 2, "ping:${ping_avg} ms");
                myLCD.setLine("network2", 3, "last ping");
                myLCD.setLine("network2", 4, "${last_ping}");
            }
        }

        netmonitor_cycle++;
    }

    private void network_connection_new() {
        // some statistics only
        log.trace("checking network status");
        Tools.check_iwconfig(current_network_stats, Tools.getIWConfig(configs.get(Configs.WIFI_CMD_LINE)));
        me.setWifi(Tools.getWifiQuality(current_network_stats.get("signal")));
        myLCD.setVariable("wifi", Tools.WIFI[me.getWifi()]);
        netmonitor_cycle++;

        if (mqtt_connected()) {


            if (Tools.fping(current_network_stats, active_broker, configs.getInt(Configs.PING_TRIES), configs.getInt(Configs.PING_TIMEOUT)))
                failed_pings_with_mqtt_connection = 0; // success
            else
                failed_pings_with_mqtt_connection++; // fail

            // too many fails
            if (failed_pings_with_mqtt_connection > configs.getLong(Configs.NETWORKING_MONITOR_DISCONNECT_AFTER_FAILED_PINGS)) {
                log.debug("too many fails - breaking up with broker {} for now", active_broker);
                disconnect_from_mqtt_broker();
            } else log.debug("we are already connected - done here");
            return; // will be reconnected with the next call to this method
        }

        String reachable_host = "";
        if (active_broker.isEmpty()) { // we only search for a new host when we weren't connected before
            log.debug("haven't had a broker yet - searching");
            ListIterator<String> brokers = potential_brokers.listIterator();
            while (reachable_host.isEmpty() && brokers.hasNext()) {
                String broker = brokers.next();
                // check quickly - only 1 try
                reachable_host = Tools.fping(current_network_stats, broker, configs.getInt(Configs.PING_TRIES), configs.getInt(Configs.PING_TIMEOUT)) ? broker : "";
            }
        } else {
            log.debug("had {} as broker before - retrying", active_broker);
            reachable_host = Tools.fping(current_network_stats, active_broker, configs.getInt(Configs.PING_TRIES), configs.getInt(Configs.PING_TIMEOUT)) ? active_broker : "";
        }
        current_network_stats.forEach((k, v) -> myLCD.setVariable(k, v));
        set_pins_to(Configs.ALL_LEDS, "off");
        pinHandler.setScheme(Configs.OUT_LED_WHITE, "netstatus"); // white is always flashing
        // still empty ?
        if (reachable_host.isEmpty()) {
            log.debug("nobody answered - failed");
            pinHandler.setScheme(Configs.OUT_LED_WHITE, "netstatus"); // white is always flashing
            if (me.getWifi() > 0) pinHandler.setScheme(Configs.OUT_LED_RED, "netstatus");
            if (me.getWifi() > 2) pinHandler.setScheme(Configs.OUT_LED_YELLOW, "netstatus");
            if (me.getWifi() > 3) pinHandler.setScheme(Configs.OUT_LED_GREEN, "netstatus");

            if (!myLCD.pageExists("network1")) {
                myLCD.welcome_page();

                myLCD.setLine("network1", 1, "ssid:${essid}");
                myLCD.setLine("network1", 2, "AP: ${ap}");
                myLCD.setLine("network1", 3, "signal:${signal}");
                myLCD.setLine("network1", 4, "wifi:${wifi}");

                myLCD.setLine("network2", 1, "${ping_host} ${ping_success}");
                myLCD.setLine("network2", 2, "ping:${ping_avg} ms");
                myLCD.setLine("network2", 3, "last ping");
                myLCD.setLine("network2", 4, "${last_ping}");
            }
        } else {
            log.debug("found host {}", reachable_host);
            myLCD.setLine("page0", 2, "Searching for broker");
            myLCD.setLine("page0", 3, "@" + reachable_host);
            disconnect_from_mqtt_broker();
            if (try_mqtt_broker(String.format("tcp://%s:%s", reachable_host, configs.get(Configs.MQTT_PORT)))) {
                myLCD.init();
                myLCD.setLine("page0", 2, "MQTT connected to");
                myLCD.setLine("page0", 3, active_broker);
                myLCD.setLine("page0", 4, "");
                pinHandler.setScheme(Configs.OUT_LED_WHITE, "netstatus"); // white is always flashing
                pinHandler.setScheme(Configs.OUT_LED_BLUE, "netstatus"); // white is always flashing
                active_broker = reachable_host;
            }
        }
    }


    private boolean mqtt_connected() {
        return iMqttClient.isPresent() && iMqttClient.get().isConnected();
    }


    @SneakyThrows
    @Override
    /**
     * this reacts very late on a network disconnect. When the broker is shut down normally
     * this method is called immediately. So this is not useful for network disconnects.
     */
    public void connectionLost(Throwable cause) {
        log.warn("connectionLost() - {}", cause.getMessage());
        disconnect_from_mqtt_broker();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        log.trace("{} - {}", topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.trace("{} - delivered", token.getMessageId());
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("mqtt connection #{} complete", mqtt_connect_tries);
    }

    /**
     * for progressing signals with a specific timer. this method is called from MyLCD.java which is in charge of
     * calculating all timers.
     *
     * @param evt A PropertyChangeEvent object describing the event source and the property that has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (progress_timer.isPresent()) led_progress_bar(evt);
        if (blinking_timer.isPresent()) led_blinking_timer(evt);
    }

    private void led_blinking_timer(PropertyChangeEvent evt) {
        // left is the timer key from mylcd
        if (!evt.getPropertyName().equalsIgnoreCase(blinking_timer.get().getRight())) return;
        if (((Long) evt.getNewValue()) == 0l) {
            pinHandler.setScheme(blinking_timer.get().getLeft(), "off");
            return;
        }
        LocalDateTime remainingTime = LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) evt.getNewValue()),
                TimeZone.getTimeZone("UTC").toZoneId());
        // update timer only when minutes change
        if (prev_progress_timer == remainingTime.getMinute()) return;
        prev_progress_timer = remainingTime.getMinute();
        pinHandler.setScheme(blinking_timer.get().getLeft(), Tools.getGametimeBlinkingScheme(remainingTime));
    }

    void led_progress_bar(PropertyChangeEvent evt) {
        if (!evt.getPropertyName().equalsIgnoreCase(progress_timer.get())) return;
        if (((Long) evt.getNewValue()) == 0l) {
            set_pins_to(Configs.ALL_LEDS, "off");
            return;
        }
        BigDecimal initial_timer = BigDecimal.valueOf((Long) evt.getOldValue());
        BigDecimal current_timer = BigDecimal.valueOf((Long) evt.getNewValue());
        BigDecimal ratio = BigDecimal.ONE.subtract(current_timer.divide(initial_timer, 2, RoundingMode.UP));
        int progress_steps = Configs.ALL_LEDS.length;
        int progress = ratio.multiply(BigDecimal.valueOf(progress_steps)).intValue();
        log.trace("timer progress ratio {}", ratio);
        //progress = Math.min(progress, progress_steps);
        log.trace("scaled to {} led stripes {}", progress_steps, progress);
        if (progress == prev_progress_timer) return; // only set LEDs when necessary
        prev_progress_timer = progress;

        List<String> leds_to_use = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(Configs.ALL_LEDS, 0, progress + 1)));
        List<String> leds_to_set_off = new ArrayList<>(Arrays.asList(Configs.ALL_LEDS));
        leds_to_set_off.removeAll(leds_to_use); // set difference
        log.debug("leds to use {} with progress {}", leds_to_use, progress);
        log.debug("leds to be off {}", leds_to_set_off);

        set_pins_to(leds_to_set_off.toArray(), "off");
        set_pins_to(leds_to_use.toArray(), PROGRESS_SCHEMES[progress]);
    }


}

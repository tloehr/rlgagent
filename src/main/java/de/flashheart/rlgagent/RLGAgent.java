package de.flashheart.rlgagent;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import de.flashheart.rlgagent.hardware.Agent;
import de.flashheart.rlgagent.hardware.abstraction.MyLCD;
import de.flashheart.rlgagent.hardware.pinhandler.PinHandler;
import de.flashheart.rlgagent.jobs.NetworkMonitoringJob;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.JavaTimeConverter;
import de.flashheart.rlgagent.misc.Tools;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Log4j2
public class RLGAgent implements MqttCallbackExtended {
    private final String scheme = "infty:on,250;off,750";
    private static final int DEBOUNCE = 200; //ms
    private int NETWORKING_MONITOR_INTERVAL = 10;
    private static final int STATUS_INTERVAL_IN_NETWORKING_MONITOR_CYCLES = 6;
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
    private SimpleTrigger networkMonitoringTrigger;
    private final JobKey networkMonitoringJob;
    private long mqtt_connect_tries = 0l;
    private long netmonitor_cycle = -1l;
    private String active_broker = "";
    private HashMap<String, String> current_network_stats;
    public static final DateTimeFormatter myformat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM);
    //UUID.randomUUID().toString(); // constant client-id during the application runtime.

    public RLGAgent(Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, PinHandler pinHandler, MyLCD myLCD) throws SchedulerException {
        //this.lock = new ReentrantLock();
        this.myUI = myUI;
        this.gpio = gpio;
        this.pinHandler = pinHandler;
        this.configs = configs;
        this.myLCD = myLCD;
        potential_brokers = Arrays.asList(configs.get(Configs.MQTT_BROKER).trim().split("\\s+"));
        current_network_stats = new HashMap<>();

        me = new Agent(new JSONObject()
                .put("agentid", configs.getAgentname())
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now())));

        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler.getContext().put("rlgAgent", this);
        this.scheduler.start();

        iMqttClient = Optional.empty();

        // inbound
        //CMD4ALL = String.format("%s/cmd/all/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());
        CMD4ME = String.format("%s/cmd/%s/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        // outbound
        EVENTS = String.format("%s/evt/%s/", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        networkMonitoringJob = new JobKey(NetworkMonitoringJob.name, "group1");

        initAgent();
        initNetworkConnection();
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
                active_broker = "";
                log.debug("closing " + iMqttClient1.getClientId());
            } catch (MqttException e) {
                log.warn(e.getMessage());
            }
        });
        iMqttClient = Optional.empty();
        log.debug("disconnected from mqtt");
    }

    /**
     * belongs to connect_to_mqtt_broker()
     *
     * @param uri to try for connection
     * @return true when connection was successful, false otherwise
     */
    private boolean try_mqtt_broker(String uri) {
        boolean success = false;

        try {
            mqtt_connect_tries++;
            log.debug("trying broker @{} number of tries: {}", uri, mqtt_connect_tries);
            //unsubscribe_from_all();
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

                // if the connection is lost, these subscriptions are lost too.
                // we need to (re)subscribe
                //iMqttClient.get().subscribe(CMD4ALL, (topic, receivedMessage) -> proc(topic, receivedMessage));
                iMqttClient.get().subscribe(CMD4ME, (topic, receivedMessage) -> proc(topic, receivedMessage));

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
            if (cmd.equalsIgnoreCase("init")) {
                procInit();
            } else if (cmd.equalsIgnoreCase("shutdown")) {
                procShutdown(true);
            } else {
                final JSONObject json = new JSONObject(new String(receivedMessage.getPayload()));
                // <cmd/> => paged|signals|timers|vars
                if (cmd.equalsIgnoreCase("paged")) {
                    procPaged(json);
                } else if (cmd.equalsIgnoreCase("delpage")) {
                    procDelPage(json);
                } else if (cmd.equalsIgnoreCase("signals")) {
                    procSignals(json);
                } else if (cmd.equalsIgnoreCase("timers")) {
                    procTimers(json);
                } else if (cmd.equalsIgnoreCase("vars")) {
                    procVars(json);
                } else {
                    log.warn("unknown command {}", cmd);
                }
            }
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    public void procShutdown(boolean system_shutdown) {
        log.trace("Shutdown initiated");

        myLCD.init();
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
        myLCD.setLine("page0", 2, system_shutdown ? "System" : "Agent");
        myLCD.setLine("page0", 3, "shutdown");

        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        disconnect_from_mqtt_broker();
        configs.saveConfigs();
        pinHandler.off();
        if (system_shutdown) Tools.system_shutdown();
        Runtime.getRuntime().halt(0);
    }

    private void procInit() {
        log.trace("received INIT");
        myLCD.init();
        pinHandler.off();
    }

    private void procTimers(JSONObject json) {
        json.keySet().forEach(sKey -> myLCD.setTimer(sKey, json.getLong(sKey)));
    }

    private void procVars(JSONObject json) {

        json.keySet().forEach(sKey -> myLCD.setVariable(sKey, json.getString(sKey)));
    }

    private void procSignals(JSONObject json) {
        Set<String> keys = json.keySet();
        if (keys.contains("all")) {
            set_pins_to(Configs.ALL, json.getString("all"));
        }
        if (keys.contains("led_all")) {
            set_pins_to(Configs.ALL_LEDS, json.getString("led_all"));
        }
        if (keys.contains("sir_all")) {
            set_pins_to(Configs.ALL_SIRENS, json.getString("sir_all"));
        }
        keys.remove("led_all");
        keys.remove("sir_all");
        keys.remove("all");

        keys.forEach(signal_key -> {
            String signal = json.getString(signal_key);
            pinHandler.setScheme(signal_key, signal);
        });
    }

    private void procPaged(JSONObject json) {
        json.keySet().forEach(page -> {
            JSONArray lines = json.getJSONArray(page);
            for (int line = 1; line <= lines.length(); line++) {
                myLCD.setLine(page, line, lines.getString(line - 1));
            }
        });
    }

    private void procDelPage(JSONObject json) {
        json.getJSONArray("page_handles").forEach(page -> myLCD.delPage(page.toString()));
    }

    private void initAgent() {
        // Hardware Buttons
        gpio.ifPresent(gpioController -> {
            GpioPinDigitalInput btn01 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN01)), PinPullResistance.PULL_UP);
            btn01.setDebounce(DEBOUNCE);
            btn01.addListener((GpioPinListenerDigital) event -> {
                log.trace("button event: {}; State: {}; Edge: {}", "btn01", event.getState(), event.getEdge());
                //if (event.getState() != PinState.LOW) return;
                if (event.getState() == PinState.HIGH)
                    reportEvent("btn01", new JSONObject().put("button", "up").toString());
                if (event.getState() == PinState.LOW)
                    reportEvent("btn01", new JSONObject().put("button", "down").toString());
            });

            GpioPinDigitalInput btn02 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN02)), PinPullResistance.PULL_UP);
            btn02.setDebounce(DEBOUNCE);
            btn02.addListener((GpioPinListenerDigital) event -> {
                log.trace("button event: {}; State: {}; Edge: {}", "btn02", event.getState(), event.getEdge());
                //if (event.getState() != PinState.LOW) return;
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
                        .withIntervalInSeconds(NETWORKING_MONITOR_INTERVAL)
                        .repeatForever())
                .build();
        scheduler.scheduleJob(job, networkMonitoringTrigger);
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
    
    private void set_pins_to(String[] pins, String scheme) {
        for (String pin : pins) {
            pinHandler.setScheme(pin, scheme);
        }
    }

    /**
     * this method is called every NETWORKING_MONITOR_INTERVAL seconds to check if network is still good. maintains a
     * global map with necessary parameters to describe the current state of the network. ** reads wifi parameters and
     * stores them ** pings the last reachable host first. if no luck, it tries out all given potential brokers from the
     * config.txt ** if the host answers the ping, and we are connected to a broker on that host -> WE ARE GOOD ** if
     * the host answers the ping, and we are NOT connected to a broker, then we try to connect to the broker on that
     * host. ** if nobody answers to our pings -> we are CLOSE a potential connection to any MQTT broker we were
     * connected to before. ** When there is No MQTT Broker connection, the agent displays the connection status on the
     * LCD and the LEDs. ** every STATUS_INTERVAL_IN_NETWORKING_MONITOR_CYCLES cycles a status message is sent out the
     * broker (if there is one)
     *
     * @throws SchedulerException
     */
    public void network_connection() throws SchedulerException {
        log.debug("checking network status");
        Tools.getWifiParams(current_network_stats, Tools.getIWConfig(configs.get(Configs.WIFI_CMD_LINE)));
        me.setWifi(Tools.getWifiQuality(current_network_stats.get("signal")));
        myLCD.setVariable("wifi", Tools.WIFI[me.getWifi()]);

        netmonitor_cycle++;

        // we assume that a raspi only uses WIFI
        //if (me.getWifi() == 0) return; // no wifi - no chance

        String reachable_host = "";
        // if we already have an active broker, we check it first

        if (!Tools.fping(current_network_stats, active_broker, 1, 500)) {
            ListIterator<String> brokers = potential_brokers.listIterator();
            while (reachable_host.isEmpty() && brokers.hasNext()) {
                String broker = brokers.next();
                reachable_host = Tools.fping(current_network_stats, broker, 1, 500) ? broker : "";
            }
        } else {
            reachable_host = active_broker;
        }

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
                    myLCD.setLine("page0", 1, "=== MQTT ===");
                    myLCD.setLine("page0", 2, "Connected to");
                    myLCD.setLine("page0", 3, active_broker);
                    myLCD.setLine("page0", 4, "");
                    set_pins_to(Configs.ALL_LEDS, "off");
                    pinHandler.setScheme(Configs.OUT_LED_WHITE, scheme); // white is always flashing
                    pinHandler.setScheme(Configs.OUT_LED_BLUE, scheme); // white is always flashing
                    netmonitor_cycle = 0; // to inform commander right away
                }
            }
            // get a better ping statistics out of 5 pings to the current WORKING broker
            //Tools.fping(current_network_stats, active_broker, 5, 500);
        } else {
            disconnect_from_mqtt_broker();
        }

        if (!mqtt_connected()) {
            set_pins_to(Configs.ALL_LEDS, "off");
            pinHandler.setScheme(Configs.OUT_LED_WHITE, scheme); // white is always flashing
            if (me.getWifi() > 0) pinHandler.setScheme(Configs.OUT_LED_RED, scheme);
            if (me.getWifi() > 2) pinHandler.setScheme(Configs.OUT_LED_YELLOW, scheme);
            if (me.getWifi() > 3) pinHandler.setScheme(Configs.OUT_LED_GREEN, scheme);

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

        // this reports a status message. very useful to see if all the agents are doing well during the game
        if (netmonitor_cycle == 0 || netmonitor_cycle % STATUS_INTERVAL_IN_NETWORKING_MONITOR_CYCLES == 0) {
            if (mqtt_connected()) {
                JSONObject status = new JSONObject(current_network_stats);
                status.put("wifi", Tools.WIFI[me.getWifi()])
                        .put("version", configs.getBuildProperties("my.version") + "." + configs.getBuildProperties("buildNumber"))
                        .put("mqtt-broker", active_broker)
                        .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                        .put("mqtt_connect_tries", mqtt_connect_tries);
                reportEvent("status", status.toString());
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
}

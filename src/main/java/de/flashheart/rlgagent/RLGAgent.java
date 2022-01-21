package de.flashheart.rlgagent;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import de.flashheart.rlgagent.hardware.Agent;
import de.flashheart.rlgagent.hardware.abstraction.MyLCD;
import de.flashheart.rlgagent.hardware.pinhandler.PinHandler;
import de.flashheart.rlgagent.jobs.MqttConnectionJob;
import de.flashheart.rlgagent.jobs.StatusJob;
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
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Log4j2
public class RLGAgent implements MqttCallbackExtended {
    private static final int DEBOUNCE = 200; //ms
    private static final int MQTT_INTERVAL_BETWEEN_CONNECTION_TRIES_IN_SECONDS = 8;
    private static final int STATUS_INTERVAL_IN_SECONDS = 60;
    private static final long MINIMUM_UPTIME_B4_SHUTDOWN_IS_PROCESSED = 60000;
    private final String EVENTS, CMD4ME, CMD4ALL;
    private final Optional<MyUI> myUI;
    private final Optional<GpioController> gpio;
    private final PinHandler pinHandler;
    private final Configs configs;
    private final MyLCD myLCD;
    private Optional<IMqttClient> iMqttClient;

    private final JobKey myConnectionJobKey, myStatusJobKey;
    private final Scheduler scheduler;
    private final Agent me;
    private SimpleTrigger connectionTrigger, statusTrigger;
    //private Optional<String> MQTT_URI;
    private long agent_connected_since = Long.MAX_VALUE;
    private long mqtt_connect_tries = 0l;
    final ReentrantLock lock;


    final String[] wifiQuality = new String[]{"dead", "ugly", "bad", "good", "excellent"};

    public RLGAgent(Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, PinHandler pinHandler, MyLCD myLCD) throws SchedulerException {
        this.lock = new ReentrantLock();
        this.myUI = myUI;
        this.gpio = gpio;
        this.pinHandler = pinHandler;
        this.configs = configs;
        this.myLCD = myLCD;
        //this.MQTT_URI = Optional.empty();

        me = new Agent(new JSONObject()
                .put("agentid", configs.getAgentname())
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                .put("wifi", Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)))));

        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler.getContext().put("rlgAgent", this);
        this.scheduler.start();

        iMqttClient = Optional.empty();

        // inbound
        CMD4ALL = String.format("%s/cmd/all/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());
        CMD4ME = String.format("%s/cmd/%s/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        // outbound
        EVENTS = String.format("%s/evt/%s/", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        myStatusJobKey = new JobKey(StatusJob.name, "group1");
        myConnectionJobKey = new JobKey(MqttConnectionJob.name, "group1");

        initAgent();
        initMqttConnectionJob();
        initStatusJob();
    }

    /**
     * the agent uses a space separated list of brokers in the config file (key 'mqtt_broker'). it tries every entry in
     * this list until a connection can be established. during a RUNTIME this broker is not changed again. even if the
     * connection drops, the agent will try to reconnect to the same broker again.
     * <p>
     * To reconnect to a different broker, the agent needs to restart.
     */
    public void connect_to_mqtt_broker() {
        show_connection_status();

        if (me.getWifi() <= 0) {
            log.warn("no wifi - no chance");
        } else {
            mqtt_connect_tries++;
            log.debug("searching for mqtt broker");
            // try all the brokers on the space separated list.
            for (String broker : Arrays.asList(configs.get(Configs.MQTT_BROKER).trim().split("\\s+"))) {
                boolean success = Tools.ping(broker) && try_mqtt_broker(String.format("tcp://%s:%s", broker, configs.get(Configs.MQTT_PORT)));
                if (success) {
                    show_connection_status();
                    break;
                }
            }
        }
    }

    private void unsubscribe_from_all() {
        // don't know if this ever comes into effect
        iMqttClient.ifPresent(iMqttClient1 -> {
            try {
                iMqttClient1.unsubscribe(CMD4ALL);
                log.debug("unsubscribing from " + CMD4ALL);
            } catch (MqttException e) {
                log.debug(e.getMessage());
            }
            try {
                iMqttClient1.unsubscribe(CMD4ME);
                log.debug("unsubscribing from " + CMD4ME);
            } catch (MqttException e) {
                log.debug(e.getMessage());
            }
            try {
                iMqttClient1.disconnect();
                log.debug("disconnecting " + iMqttClient1.getClientId());
            } catch (MqttException e) {
                log.debug(e.getMessage());
            }
            try {
                iMqttClient1.close();
                log.debug("closing " + iMqttClient1.getClientId());
            } catch (MqttException e) {
                log.debug(e.getMessage());
            }
        });
    }

    /**
     * belongs to connect_to_mqtt_broker()
     *
     * @param uri to try for connection
     * @return true when connection was successful, false otherwise
     */
    boolean try_mqtt_broker(String uri) {
        boolean success = false;

        try {
            log.debug("trying broker @{} number of tries: {}", uri, mqtt_connect_tries);
            unsubscribe_from_all();
            iMqttClient = Optional.of(new MqttClient(uri, String.format("%s#%d-%s", me.getAgentid(), mqtt_connect_tries, UUID.randomUUID()) + mqtt_connect_tries, new MqttDefaultFilePersistence(configs.getWORKSPACE())));
            MqttConnectOptions options = new MqttConnectOptions();
            // todo: this needs to go into the configs
            options.setAutomaticReconnect(true); // todo: really ? Try with false.
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setMaxInflight(1000);

            if (iMqttClient.isPresent()) {
                iMqttClient.get().connect(options);
                iMqttClient.get().setCallback(this);
            }

            if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
                mqtt_connect_tries = 0;
                log.info("Connected to the broker @{} with ID: {}", iMqttClient.get().getServerURI(), iMqttClient.get().getClientId());
                // stop the connection job - we are done here
                try {
                    scheduler.interrupt(myConnectionJobKey);
                    scheduler.unscheduleJob(connectionTrigger.getKey());
                    scheduler.deleteJob(myConnectionJobKey);
                } catch (SchedulerException e) {
                    log.warn(e);
                }

                // if the connection is lost, these subscriptions are lost too.
                // we need to (re)subscribe
                iMqttClient.get().subscribe(CMD4ALL, (topic, receivedMessage) -> proc(topic, receivedMessage));
                iMqttClient.get().subscribe(CMD4ME, (topic, receivedMessage) -> proc(topic, receivedMessage));

                agent_connected_since = System.currentTimeMillis();
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
            } else if (cmd.equalsIgnoreCase("status")) {
                procStatus();
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
        log.debug("Shutdown initiated");
        // todo: this is not good. maybe shutdowns should not retain.
        long connection_up_time = System.currentTimeMillis() - agent_connected_since;
        log.debug("connection uptime in seconds {}", connection_up_time / 1000);
        if (connection_up_time < MINIMUM_UPTIME_B4_SHUTDOWN_IS_PROCESSED) {
            log.warn("shutdown arrived message too soon after last connection. discarding.");
            return;
        }

        myLCD.init();
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
        myLCD.setLine("page0", 2, system_shutdown ? "System" : "Agent");
        myLCD.setLine("page0", 3, "shutdown");

        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        unsubscribe_from_all();
        configs.saveConfigs();
        pinHandler.off();
        if (system_shutdown) Tools.system_shutdown();
        Runtime.getRuntime().halt(0);
    }

    private void procInit() {
        log.debug("received INIT");
        myLCD.init();
        myLCD.setVariable("wifi", wifiQuality[me.getWifi()]);
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
        myLCD.setLine("page0", 2, "WIFI: ${wifi}");
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
                log.debug("button event: {}; State: {}; Edge: {}", "btn01", event.getState(), event.getEdge());
                //if (event.getState() != PinState.LOW) return;
                if (event.getState() == PinState.HIGH)
                    reportEvent("btn01", new JSONObject().put("button", "up").toString());
                if (event.getState() == PinState.LOW)
                    reportEvent("btn01", new JSONObject().put("button", "down").toString());
            });

            GpioPinDigitalInput btn02 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN02)), PinPullResistance.PULL_UP);
            btn02.setDebounce(DEBOUNCE);
            btn02.addListener((GpioPinListenerDigital) event -> {
                log.debug("button event: {}; State: {}; Edge: {}", "btn02", event.getState(), event.getEdge());
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

    }

    /**
     * start a job that tries to connect to the mqtt broker
     *
     * @throws SchedulerException
     */
    private void initMqttConnectionJob() throws SchedulerException {
        if (scheduler.checkExists(myConnectionJobKey)) return;
        log.debug("initMqttConnectionJob()");
        JobDetail job = newJob(MqttConnectionJob.class)
                .withIdentity(myConnectionJobKey)
                .build();

        connectionTrigger = newTrigger()
                .withIdentity(MqttConnectionJob.name + "-trigger", "group1")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(MQTT_INTERVAL_BETWEEN_CONNECTION_TRIES_IN_SECONDS)
                        .repeatForever())
                .build();
        scheduler.scheduleJob(job, connectionTrigger);
    }

    /**
     * start a job that tries to connect to the mqtt broker
     *
     * @throws SchedulerException
     */
    private void initStatusJob() throws SchedulerException {
        if (scheduler.checkExists(myStatusJobKey)) return;
        JobDetail job = newJob(StatusJob.class)
                .withIdentity(myStatusJobKey)
                .build();

        statusTrigger = newTrigger()
                .withIdentity(StatusJob.name + "-trigger", "group1")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(STATUS_INTERVAL_IN_SECONDS)
                        .repeatForever())
                .build();
        scheduler.scheduleJob(job, statusTrigger);
    }


//    private void reportEvent(String src) {
//        reportEvent(src, "");
//    }

    private void reportEvent(String src, String payload) {
        if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
            try {
                MqttMessage msg = new MqttMessage();
                msg.setQos(2);
                msg.setRetained(false);
                if (!payload.isEmpty()) msg.setPayload(payload.getBytes());
                iMqttClient.get().publish(EVENTS + src, msg);
                log.info(EVENTS);
            } catch (MqttException mqe) {
                log.warn(mqe);
            }
        }
    }

    /**
     * shows the current connection status via LED signals and LCD output
     * <p>
     * white - agent is running and trying to connect red - green - signal strength for wifi blue - mqtt is connected
     */
    private void show_connection_status() {
        me.setWifi(Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE))));

        String scheme = "∞:on,250;off,1750";

        pinHandler.setScheme(Configs.OUT_LED_WHITE, scheme); // white is always flashing
        pinHandler.setScheme(Configs.OUT_LED_RED, "off");
        pinHandler.setScheme(Configs.OUT_LED_YELLOW, "off");
        pinHandler.setScheme(Configs.OUT_LED_GREEN, "off");
        pinHandler.setScheme(Configs.OUT_LED_BLUE, "off");

        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
        myLCD.setLine("page0", 4, "WIFI: " + Tools.WIFI[me.getWifi()]);

        if (me.getWifi() <= 0) {
            myLCD.setLine("page0", 2, "");
            myLCD.setLine("page0", 3, "");
            myLCD.setLine("page0", 4, "NO WIFI");
        } else if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
            myLCD.setLine("page0", 1, "RLGAgent");
            myLCD.setLine("page0", 2, "Ver. ${agversion}.${agbuild}");
            myLCD.setLine("page0", 3, "MQTT found @");
            myLCD.setLine("page0", 4, iMqttClient.get().getServerURI());
            pinHandler.setScheme(Configs.OUT_LED_BLUE, scheme);
        } else {
            myLCD.setLine("page0", 2, "Searching for");
            myLCD.setLine("page0", 3, "MQTT Broker " + mqtt_connect_tries + "x");
            if (me.getWifi() > 0) pinHandler.setScheme(Configs.OUT_LED_RED, scheme);
            if (me.getWifi() > 1) pinHandler.setScheme(Configs.OUT_LED_YELLOW, scheme);
            if (me.getWifi() > 2) pinHandler.setScheme(Configs.OUT_LED_GREEN, scheme);
        }
    }

    /**
     * inform commander about my current status and what I am capable of (role) is repeated every 60 seconds by the
     * StatusJob
     */
    public void procStatus() {
        int wifi = Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)));
        if (wifi != me.getWifi()) {
            me.setWifi(wifi);
            myLCD.setVariable("wifi", wifiQuality[me.getWifi()]);
        }
        reportEvent("status", new JSONObject()
                .put("wifi", me.getWifi())
                .put("version", configs.getBuildProperties("my.version") + "." + configs.getBuildProperties("buildNumber"))
                .put("mqtt-broker", iMqttClient.isPresent() ? iMqttClient.get().getServerURI() : "not connected")
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                .toString()
        );
    }

    @SneakyThrows
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("connection to broker lost - {}", cause.getMessage());
        cause.printStackTrace();
        initMqttConnectionJob();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        log.trace("{} - {}", topic, message);
    }

    private void set_pins_to(String[] pins, String scheme) {
        for (String pin : pins) {
            pinHandler.setScheme(pin, scheme);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.trace("{} - delivered", token.getMessageId());
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        agent_connected_since = Long.MAX_VALUE;
        log.info("connection #{} complete", mqtt_connect_tries);
    }
}

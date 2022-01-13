package de.flashheart.rlgagent;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import de.flashheart.rlgagent.hardware.Agent;
import de.flashheart.rlgagent.hardware.abstraction.MyLCD;
import de.flashheart.rlgagent.hardware.pinhandler.PinHandler;
import de.flashheart.rlgagent.jobs.MqttConnectionJob;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Log4j2
public class RLGAgent implements MqttCallbackExtended {
    private static final int DEBOUNCE = 200; //ms
    private static final int MQTT_INTERVAL_BETWEEN_CONNECTION_TRIES_IN_SECONDS = 8;
    private static final long MINIMUM_UPTIME_B4_SHUTDOWN_IS_PROCESSED = 60000;
    private final String EVENTS, CMD4ME, CMD4ALL;
    private final Optional<MyUI> myUI;
    private final Optional<GpioController> gpio;
    private final PinHandler pinHandler;
    private final Configs configs;
    private final MyLCD myLCD;
    private Optional<IMqttClient> iMqttClient;

    private final JobKey myConnectionJobKey;
    private final Scheduler scheduler;
    private final Agent me;
    private SimpleTrigger connectionTrigger;
    private Optional<String> MQTT_URI;
    private long agent_connected_since = Long.MAX_VALUE;


    final String[] wifiQuality = new String[]{"dead", "ugly", "bad", "good", "excellent"};

    public RLGAgent(Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, PinHandler pinHandler, MyLCD myLCD) throws SchedulerException {
        this.myUI = myUI;
        this.gpio = gpio;
        this.pinHandler = pinHandler;
        this.configs = configs;
        this.myLCD = myLCD;
        this.MQTT_URI = Optional.empty();

        me = new Agent(new JSONObject()
                .put("agentid", configs.getAgentname())
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                .put("wifi", Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)))));

        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler.getContext().put("rlgAgent", this);
        this.scheduler.start();

        iMqttClient = Optional.empty();
        /**
         * channel structure
         * inbound
         * rlg/cmd/<agentid/>/<cmd/>
         * rlg/cmd/all/<cmd/>
         *
         * outbound
         * rlg/evt/<agentid/>/<src/>
         *
         * <cmd/> => paged|signals|timers|vars|init|shutdown
         * <src/> => btn1|btn2|btn3|stat
         */

        // rlg/<gameid/>
        //PREFIX = String.format("%s/%s", configs.get(Configs.MQTT_ROOT), GAMEID);
        // inbound
        CMD4ALL = String.format("%s/cmd/all/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());
        CMD4ME = String.format("%s/cmd/%s/#", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        // outbound
        EVENTS = String.format("%s/evt/%s/", configs.get(Configs.MQTT_ROOT), me.getAgentid());

        // myStatusJobKey = new JobKey(StatusJob.name, "group1");
        myConnectionJobKey = new JobKey(MqttConnectionJob.name, "group1");

        initAgent();
        initMqttConnectionJob();
    }

    /**
     * the agent uses a space separated list of brokers in the config file (key 'mqtt_broker'). it tries every entry in
     * this list until a connection can be established. during a RUNTIME this broker is not changed again. even if the
     * connection drops, the agent will try to reconnect to the same broker again.
     * <p>
     * To reconnect to a different broker, the agent needs to restart.
     */
    public void connect_to_mqtt_broker() {
        // the wifi quality might be of interest during that phase
        int wifi = Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)));
        if (wifi != me.getWifi()) me.setWifi(wifi);
        boolean success = false;

        if (MQTT_URI.isPresent()) { // we already had a working broker. going to stick with it.
            log.debug("we already had a working broker. trying to REconnect to it");
            success = try_mqtt_broker(MQTT_URI.get());
        } else {
            log.debug("searching for mqtt broker");
            // try all the brokers on the space separated list.
            for (String broker : Arrays.asList(configs.get(Configs.MQTT_BROKER).trim().split("\\s+"))) {
                success = try_mqtt_broker(String.format("tcp://%s:%s", broker, configs.get(Configs.MQTT_PORT)));
                if (success) break;
            }
        }

        if (!success) show_connection_status_as_signals();

    }

    /**
     * belongs to connect_to_mqtt_broker()
     *
     * @param uri to try for connection
     * @return true when connection was successful, false otherwise
     */
    boolean try_mqtt_broker(String uri) {
        boolean success = false;
        log.debug("trying broker @{}", uri);
        try {
            iMqttClient = Optional.of(new MqttClient(uri, me.getMqttClientId(), new MqttDefaultFilePersistence(configs.getWORKSPACE())));
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(10);

            if (iMqttClient.isPresent()) {
                iMqttClient.get().connect(options);
                iMqttClient.get().setCallback(this);
            }

            if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
                log.info("Connected to the broker @{}", iMqttClient.get().getServerURI());
                MQTT_URI = Optional.of(uri); // we will stick with this URI from now on
                // stop the connection job - we are done here
                try {
                    scheduler.interrupt(myConnectionJobKey);
                    scheduler.unscheduleJob(connectionTrigger.getKey());
                    scheduler.deleteJob(myConnectionJobKey);
                } catch (SchedulerException e) {
                    log.warn(e);
                }

                pinHandler.off();
                pinHandler.setScheme(Configs.OUT_LED_WHITE, "very_fast"); // white is always flashing
                myLCD.setLine("page0", 1, "RLGAgent");
                myLCD.setLine("page0", 2, "Ver. ${agversion}.${agbuild}");
                myLCD.setLine("page0", 3, "MQTT found @");
                myLCD.setLine("page0", 4, uri);

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
        log.debug("received SHUTDOWN");

        if (System.currentTimeMillis() - agent_connected_since < MINIMUM_UPTIME_B4_SHUTDOWN_IS_PROCESSED) {
            log.warn("shutdown message too soon after connection. discarding.");
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
        iMqttClient.ifPresent(iMqttClient1 -> {
            try {
                iMqttClient1.disconnect();
                iMqttClient1.close();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });
        configs.saveConfigs();
        pinHandler.off();
        if (system_shutdown) Tools.system_shutdown();
        //System.exit(0);
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
            GpioPinDigitalInput bnt01 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN01)), PinPullResistance.PULL_UP);
            bnt01.setDebounce(DEBOUNCE);
            bnt01.addListener((GpioPinListenerDigital) event -> {
                if (event.getState() != PinState.LOW) return;
                reportEvent("btn01");
            });

            GpioPinDigitalInput bnt02 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN02)), PinPullResistance.PULL_UP);
            bnt02.setDebounce(DEBOUNCE);
            bnt02.addListener((GpioPinListenerDigital) event -> {
                if (event.getState() != PinState.LOW) return;
                reportEvent("btn02");
            });
        });

        // Software Buttons
        myUI.ifPresent(myUI1 -> {
            myUI1.addActionListenerToBTN01(e -> reportEvent("btn01"));
            myUI1.addActionListenerToBTN02(e -> reportEvent("btn02"));
        });

    }

    /**
     * start a job that tries to connect to the mqtt broker
     *
     * @throws SchedulerException
     */
    private void initMqttConnectionJob() throws SchedulerException {
        if (scheduler.checkExists(myConnectionJobKey)) return;
        agent_connected_since = Long.MAX_VALUE;
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


    private void reportEvent(String src) {
        reportEvent(src, "");
    }

    private void reportEvent(String src, String payload) {
        if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
            try {
                MqttMessage msg = new MqttMessage();
                msg.setQos(2);
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
    private void show_connection_status_as_signals() {
        int wifi = me.getWifi();

        pinHandler.setScheme(Configs.OUT_LED_WHITE, "normal"); // white is always flashing
        if (wifi > 0) pinHandler.setScheme(Configs.OUT_LED_RED, "normal");
        if (wifi > 1) pinHandler.setScheme(Configs.OUT_LED_YELLOW, "normal");
        if (wifi > 2) pinHandler.setScheme(Configs.OUT_LED_GREEN, "normal");
        if (wifi > 3) pinHandler.setScheme(Configs.OUT_LED_BLUE, "normal");
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
        myLCD.setLine("page0", 4, "WIFI: " + Tools.WIFI[wifi]);

        if (!iMqttClient.isPresent() || !iMqttClient.get().isConnected()) {
            if (me.getWifi() <= 0) { // no wifi
                myLCD.setLine("page0", 2, "");
                myLCD.setLine("page0", 3, "");
                myLCD.setLine("page0", 4, "NO WIFI");
            } else { // wifi but no broker yet
                myLCD.setLine("page0", 2, "Searching for");
                myLCD.setLine("page0", 3, "MQTT Broker");
            }
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
        reportEvent("status", me.toJson().toString());
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
    }
}

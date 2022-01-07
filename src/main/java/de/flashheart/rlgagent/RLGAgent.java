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
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Log4j2
public class RLGAgent implements MqttCallbackExtended {
    private static final int DEBOUNCE = 200; //ms
    private static final int MQTT_INTERVAL_BETWEEN_CONNECTION_TRIES_IN_SECONDS = 8;
    private final String TOPIC_GAMEID = "g1"; // maybe for multiple games in a later version
    private final String EVT, PREFIX, PAGED, SIGNAL, INIT, SHUTDOWN, TIMERS, VARS;
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
    private HashSet<String> group_channels; // to keep track of additional subscriptions
    private Optional<String> MQTT_URI;

    final String[] wifiQuality = new String[]{"dead", "ugly", "bad", "good", "excellent"};

    public RLGAgent(Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, PinHandler pinHandler, MyLCD myLCD) throws SchedulerException {
        this.myUI = myUI;
        this.gpio = gpio;
        this.pinHandler = pinHandler;
        this.configs = configs;
        this.myLCD = myLCD;
        this.MQTT_URI = Optional.empty();

        me = new Agent(new JSONObject()
                .put("agentid", configs.get(Configs.MY_ID))
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                .put("wifi", Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)))));

        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler.getContext().put("rlgAgent", this);
        this.scheduler.start();

        iMqttClient = Optional.empty();
        /**
         * channel structure
         * rlg/g1/paged/ag01
         * rlg/g1/signal/ag01
         * rlg/g1/timers/ag01
         * rlg/g1/vars/ag01
         * rlg/g1/evt/ag01
         * rlg/g1/init "all"
         * rlg/g1/shutdown "all"
         */
        String myid = configs.get(Configs.MY_ID);
        PREFIX = String.format("%s/%s", configs.get(Configs.MQTT_ROOT), TOPIC_GAMEID);
        EVT = String.format("%s/evt/%s", PREFIX, myid);
        PAGED = String.format("%s/paged/%s", PREFIX, myid);
        SIGNAL = String.format("%s/signal/%s", PREFIX, myid);
        TIMERS = String.format("%s/timers/%s", PREFIX, myid);
        VARS = String.format("%s/vars/%s", PREFIX, myid);
        INIT = String.format("%s/init", PREFIX);
        SHUTDOWN = String.format("%s/shutdown", PREFIX);


        group_channels = new HashSet();
        // myStatusJobKey = new JobKey(StatusJob.name, "group1");
        myConnectionJobKey = new JobKey(MqttConnectionJob.name, "group1");

        //lcdPage = myLCD.addPage();
        myLCD.setLine("page0", 3, "Ready 4");
        myLCD.setLine("page0", 4, "Action");

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

        if (MQTT_URI.isPresent()) { // we already had a working broker. going to stick with it.
            log.debug("we already had a working broker. trying to REconnect to it");
            try_mqtt_broker(MQTT_URI.get());
        } else {
            log.debug("searching for mqtt broker");
            // try all the brokers on the space separated list.
            for (String broker : Arrays.asList(configs.get(Configs.MQTT_BROKER).trim().split("\\s+"))) {
                if (try_mqtt_broker(String.format("tcp://%s:%s", broker, configs.get(Configs.MQTT_PORT)))) {
                    break;
                }
            }
        }
        show_connection_status_as_signals();
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
            iMqttClient = Optional.of(new MqttClient(uri, configs.get(Configs.MYUUID), new MqttDefaultFilePersistence(System.getProperty("workspace"))));
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
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

                // if the connection is lost, these subscriptions are lost too.
                // we need to (re)subscribe
                iMqttClient.get().subscribe(PAGED, (topic, receivedMessage) -> procPaged(receivedMessage));
                iMqttClient.get().subscribe(SIGNAL, (topic, receivedMessage) -> procSignals(receivedMessage));
                iMqttClient.get().subscribe(TIMERS, (topic, receivedMessage) -> procTimers(receivedMessage));
                iMqttClient.get().subscribe(VARS, (topic, receivedMessage) -> procVars(receivedMessage));
                iMqttClient.get().subscribe(INIT, (topic, receivedMessage) -> procInit(receivedMessage));
                iMqttClient.get().subscribe(SHUTDOWN, (topic, receivedMessage) -> procShutdown());
                success = true;
            }
        } catch (MqttException e) {
            iMqttClient = Optional.empty();
            log.warn(e.getMessage());
            success = false;
        }
        return success;
    }

    private void procShutdown() {
        myLCD.init();
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
        myLCD.setLine("page0", 2, "System");
        myLCD.setLine("page0", 3, "shutdown");
        Main.prepareShutdown();
        Tools.system_shutdown("/opt/rlgagent/shutdown.sh");
        System.exit(0);
    }

    private void procInit(MqttMessage receivedMessage) {
        myLCD.init();
        myLCD.setVariable("wifi", wifiQuality[me.getWifi()]);
        pinHandler.off();
    }

    private void procTimers(MqttMessage receivedMessage) {
        final JSONObject timers = new JSONObject(new String(receivedMessage.getPayload()));
        timers.keySet().forEach(sKey -> myLCD.setTimer(sKey, timers.getLong(sKey)));
    }

    private void procVars(MqttMessage receivedMessage) {
        final JSONObject vars = new JSONObject(new String(receivedMessage.getPayload()));
        vars.keySet().forEach(sKey -> myLCD.setVariable(sKey, vars.getString(sKey)));
    }

    private void procSignals(MqttMessage receivedMessage) {
        final JSONObject signals = new JSONObject(new String(receivedMessage.getPayload()));
        Set<String> keys = signals.keySet();
        if (keys.contains("all")) {
            set_pins_to(Configs.ALL, signals.getString("all"));
        }
        if (keys.contains("led_all")) {
            set_pins_to(Configs.ALL_LEDS, signals.getString("led_all"));
        }
        if (keys.contains("sir_all")) {
            set_pins_to(Configs.ALL_SIRENS, signals.getString("sir_all"));
        }
        keys.remove("led_all");
        keys.remove("sir_all");
        keys.remove("all");

        keys.forEach(signal_key -> {
            String signal = signals.getString(signal_key);
            pinHandler.setScheme(signal_key, signal);
        });
    }

    private void procPaged(MqttMessage receivedMessage) {
        final JSONObject pages = new JSONObject(new String(receivedMessage.getPayload()));
        pages.keySet().forEach(page -> {
            JSONArray lines = pages.getJSONArray(page);
            for (int line = 1; line <= lines.length(); line++) {
                myLCD.setLine(page, line, lines.getString(line - 1));
            }
        });
    }

//    private void processSubscription(MqttMessage receivedMessage) {
//        final JSONArray subs = new JSONArray(new String(receivedMessage.getPayload()));
//        subs.toList().forEach(sub -> {
//            try {
//                String additional_channel = String.format("%s/%s", PREFIX, sub.toString());
//                if (!group_channels.contains(additional_channel)) {
//                    iMqttClient.get().subscribe(additional_channel, (topic, msg) -> processCommand(msg));
//                    group_channels.add(additional_channel);
//                    log.debug("subscribing to {}", additional_channel);
//                }
//            } catch (MqttException e) {
//                log.warn(e);
//            }
//        });
//    }

    private void initAgent() {
        // Hardware Buttons
        gpio.ifPresent(gpioController -> {
            GpioPinDigitalInput bnt01 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN01)), PinPullResistance.PULL_UP);
            bnt01.setDebounce(DEBOUNCE);
            bnt01.addListener((GpioPinListenerDigital) event -> {
                if (event.getState() != PinState.LOW) return;
                publishMessage(new JSONObject().put("agentid", me.getAgentid()).put("button_pressed", "btn01").toString());
            });

            GpioPinDigitalInput bnt02 = gpioController.provisionDigitalInputPin(RaspiPin.getPinByName(configs.get(Configs.IN_BTN02)), PinPullResistance.PULL_UP);
            bnt02.setDebounce(DEBOUNCE);
            bnt02.addListener((GpioPinListenerDigital) event -> {
                if (event.getState() != PinState.LOW) return;
                publishMessage(new JSONObject().put("agentid", me.getAgentid()).put("button_pressed", "btn02").toString());
            });
        });

        // Software Buttons
        myUI.ifPresent(myUI1 -> {
            myUI1.addActionListenerToBTN01(e -> publishMessage(new JSONObject().put("agentid", me.getAgentid()).put("button_pressed", "btn01").toString()));
            myUI1.addActionListenerToBTN02(e -> publishMessage(new JSONObject().put("agentid", me.getAgentid()).put("button_pressed", "btn02").toString()));
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
     * messages from the commander
     *
     * @param receivedMessage
     */
    private void processCommand(MqttMessage receivedMessage) {
        try {
            final JSONObject cmds = new JSONObject(new String(receivedMessage.getPayload()));
            cmds.keySet().forEach(key -> {
                log.debug("handling command '{}' with {}", key, cmds.get(key).toString());
                switch (key.toLowerCase()) {
//                    case "page_content": {
//                        JSONObject display_cmds = cmds.getJSONObject("page_content");
//                        display_cmds.keySet().forEach(page -> {
//                            JSONArray lines = display_cmds.getJSONArray(page);
//                            for (int line = 1; line <= lines.length(); line++) {
//                                myLCD.setLine(page, line, lines.getString(line - 1));
//                            }
//                        });
//                        break;
//                    }
//                    case "add_pages": { // Adds a display page which will be addressed by this handle
//                        JSONArray cmdlist = cmds.getJSONArray("add_pages");
//                        cmdlist.forEach(page -> myLCD.addPage(page.toString().trim()));
//                        break;
//                    }
//                    case "del_pages": { // deletes one or more display pages
//                        JSONArray cmdlist = cmds.getJSONArray("del_pages");
//                        cmdlist.forEach(page -> myLCD.delPage(page.toString().trim()));
//                        break;
//                    }
//                    case "matrix_display": {
//                        JSONArray cmdlist = cmds.getJSONArray("matrix_display");
//                        for (int line = 0; line < cmdlist.length(); line++) {
//                            log.debug(cmdlist.getString(line));
//                        }
//                        break;
//                    }
//                    case "signal": {
//                        final JSONObject signals = cmds.getJSONObject("signal");
//
//                        // JSON Keys are unordered by definition. So we need to look first for led_all and sir_all.
//                        Set<String> keys = signals.keySet();
//                        if (keys.contains("all")) {
//                            set_pins_to(Configs.ALL, signals.getString("all"));
//                        }
//                        if (keys.contains("led_all")) {
//                            set_pins_to(Configs.ALL_LEDS, signals.getString("led_all"));
//                        }
//                        if (keys.contains("sir_all")) {
//                            set_pins_to(Configs.ALL_SIRENS, signals.getString("sir_all"));
//                        }
//                        keys.remove("led_all");
//                        keys.remove("sir_all");
//                        keys.remove("all");
//
//                        // cycle through the rest of the signals - one by one
//                        keys.forEach(signal_key -> {
//                            String signal = signals.getString(signal_key);
//                            pinHandler.setScheme(signal_key, signal);
//                        });
//                        break;
//                    }
                    case "status": {
                        sendStatus();
                        break;
                    }
//                    case "timers": {
//                        JSONObject timers = cmds.getJSONObject("timers");
//                        timers.keySet().forEach(sKey -> myLCD.setTimer(sKey, timers.getLong(sKey)));
//                        break;
//                    }
//                    case "vars": {
//                        JSONObject vars = cmds.getJSONObject("vars");
//                        vars.keySet().forEach(sKey -> myLCD.setVariable(sKey, vars.getString(sKey)));
//                        break;
//                    }
//                    case "shutdown": {
//                        myLCD.init();
//                        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");
//                        myLCD.setLine("page0", 2, "System");
//                        myLCD.setLine("page0", 3, "shutdown");
//                        Main.prepareShutdown();
//                        Tools.system_shutdown("/opt/rlgagent/shutdown.sh");
//                        System.exit(0);
//                        break;
//                    }
//                    case "init": {
//                        unsubscribe_from_additional_subscriptions();
//                        myLCD.init();
//                        myLCD.setVariable("wifi", wifiQuality[me.getWifi()]);
//                        pinHandler.off();
//                        //show_connection_status_as_signals();
//                        break;
//                    }
                    default: {
                        log.warn("unknown command - ignoring");
                        break;
                    }
                }
            });
        } catch (JSONException e) {
            log.warn(e);
        }
    }


    private void unsubscribe_from_additional_subscriptions() {
        log.debug("unsubscribing from all additional subscriptions");
        group_channels.forEach(s -> {
            try {
                iMqttClient.get().unsubscribe(s);
            } catch (MqttException e) {
                log.warn(e);
            }
        });
        group_channels.clear();
    }

    private void publishMessage(String payload) {
        if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
            try {
                MqttMessage msg = new MqttMessage();
                msg.setQos(2);
                msg.setRetained(true);
                msg.setPayload(payload.getBytes());
                //payload.ifPresent(string -> msg.setPayload(string.getBytes()));
                iMqttClient.get().publish(EVT, msg);
                log.info(EVT);
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
        myLCD.setLine("page0", 1, "RLGAgent ${agversion}.${agbuild}");

        pinHandler.setScheme(Configs.OUT_LED_WHITE, "normal"); // white is always flashing
        if (wifi > 0) pinHandler.setScheme(Configs.OUT_LED_RED, "normal");
        if (wifi > 1) pinHandler.setScheme(Configs.OUT_LED_YELLOW, "normal");
        if (wifi > 2) pinHandler.setScheme(Configs.OUT_LED_GREEN, "normal");
        if (iMqttClient.isPresent() && iMqttClient.get().isConnected())
            pinHandler.setScheme(Configs.OUT_LED_BLUE, "normal");
        if (me.getWifi() <= 0) { // no wifi
            myLCD.setLine("page0", 2, "");
            myLCD.setLine("page0", 3, "");
            myLCD.setLine("page0", 4, "NO WIFI");
        } else if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) { // up and running
            myLCD.setLine("page0", 2, "WIFI: " + Tools.WIFI[wifi].toLowerCase());
            myLCD.setLine("page0", 3, "connected to");
            myLCD.setLine("page0", 4, MQTT_URI.orElse("?? ERROR ?? WTF ??"));
        } else { // wifi but no broker yet
            myLCD.setLine("page0", 2, "WIFI: " + Tools.WIFI[wifi]);
            myLCD.setLine("page0", 3, "Searching for");
            myLCD.setLine("page0", 4, "MQTT Broker");
        }
    }

    /**
     * inform commander about my current status and what I am capable of (role) is repeated every 60 seconds by the
     * StatusJob
     */
    public void sendStatus() {
        int wifi = Tools.getWifiQuality(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)));
        if (wifi != me.getWifi()) {
            me.setWifi(wifi);
            myLCD.setVariable("wifi", wifiQuality[me.getWifi()]);
        }
        publishMessage(new JSONObject().put("status", me.toJson()).toString());
    }

    @SneakyThrows
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("connection to broker lost - {}", cause.getMessage());
        cause.printStackTrace();
        initMqttConnectionJob();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        log.debug("{} - {}", topic, message);
    }

    private void set_pins_to(String[] pins, String scheme) {
        for (String pin : pins) {
            pinHandler.setScheme(pin, scheme);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("{} - delivered", token.getMessageId());
    }

    public void shutdown() {
        try {
//            myLCD.init();
//            myLCD.setLine("page0", 2, "agent");
//            myLCD.setLine("page0", 3, "shutdown");
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
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
    }
}

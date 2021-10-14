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
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Log4j2
public class RLGAgent implements MqttCallbackExtended {
    private static final int DEBOUNCE = 200; //ms
    private final String TOPIC_EVENT_FROM_ME;
    private final String TOPIC_PREFIX;
    private final String TOPIC_CMD_ME;
    private final String TOPIC_CMD_ALL;
    private final Optional<MyUI> myUI;
    private final Optional<GpioController> gpio;
    private final PinHandler pinHandler;
    private final Configs configs;
    private final MyLCD myLCD;
    private Optional<IMqttClient> iMqttClient;
    int lcdPage = 0;
    private final JobKey myStatusJobKey, myConnectionJobKey;
    private final Scheduler scheduler;
    private final Agent me;
    private SimpleTrigger connectionTrigger;
    private HashSet<String> group_channels; // to keep track of additional subscriptions

    public RLGAgent(Configs configs, Optional<MyUI> myUI, Optional<GpioController> gpio, PinHandler pinHandler, MyLCD myLCD) throws SchedulerException {
        this.myUI = myUI;
        this.gpio = gpio;
        this.pinHandler = pinHandler;
        this.configs = configs;
        this.myLCD = myLCD;

        String wifiResponse = Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE));

        me = new Agent(new JSONObject()
                .put("agentid", configs.get(Configs.MY_ID))
                .put("gameid", configs.get(Configs.GAME_ID))
                .put("timestamp", JavaTimeConverter.to_iso8601(LocalDateTime.now()))
                .put("wifi_response_by_driver", wifiResponse)
                .put(Configs.HAS_SIRENS, configs.get(Configs.HAS_SIRENS, "false"))
                .put(Configs.HAS_LEDS, configs.get(Configs.HAS_LEDS, "false"))
                .put(Configs.HAS_LINE_DISPLAY, configs.get(Configs.HAS_LINE_DISPLAY, "false"))
                .put(Configs.HAS_MATRIX_DISPLAY, configs.get(Configs.HAS_MATRIX_DISPLAY, "false"))
                .put(Configs.HAS_SOUND, configs.get(Configs.HAS_SOUND, "false"))
                .put(Configs.HAS_RFID, configs.get(Configs.HAS_RFID, "false")));

        this.scheduler = StdSchedulerFactory.getDefaultScheduler();
        this.scheduler.getContext().put("rlgAgent", this);
        this.scheduler.start();

        iMqttClient = Optional.empty();
        TOPIC_PREFIX = String.format("%s/cmd", configs.get(Configs.GAME_ID));
        TOPIC_CMD_ME = String.format("%s/%s", TOPIC_PREFIX, configs.get(Configs.MY_ID));
        TOPIC_CMD_ALL = String.format("%s/all", TOPIC_PREFIX);
        TOPIC_EVENT_FROM_ME = String.format("%s/evt/%s", configs.get(Configs.GAME_ID), configs.get(Configs.MY_ID));
        group_channels = new HashSet();
        myStatusJobKey = new JobKey(StatusJob.name, "group1");
        myConnectionJobKey = new JobKey(MqttConnectionJob.name, "group1");

        //lcdPage = myLCD.addPage();
        myLCD.setLine(lcdPage, 1, "Ready 4");
        myLCD.setLine(lcdPage, 2, "Action");

        initAgent();
        //initHeartbeatJob();
        initMqttConnectionJob();
        //waitForCommander();
    }

    /**
     * this method is called from the connection job - not directly
     */
    public void initMQttClient() {
        // the wifi quality might be of interest during that phase
        me.setWifi_response_by_driver(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)));
        myLCD.setWifi(me.getWifi_response_by_driver());
        waitForCommander();

        try {
            iMqttClient = Optional.ofNullable(new MqttClient(configs.get(Configs.MQTT_BROKER), configs.get(Configs.MYUUID), new MqttDefaultFilePersistence(System.getProperty("workspace"))));
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
                // stop the connection job
                try {
                    scheduler.interrupt(myConnectionJobKey);
                    scheduler.unscheduleJob(connectionTrigger.getKey());
                    scheduler.deleteJob(myConnectionJobKey);
                } catch (SchedulerException e) {
                    log.warn(e);
                }

                // if the connection is lost, these subscriptions are lost too.
                // we need to (re)subscribe
                iMqttClient.get().subscribe(TOPIC_CMD_ME, (topic, receivedMessage) -> processCommand(receivedMessage));
                iMqttClient.get().subscribe(TOPIC_CMD_ALL, (topic, receivedMessage) -> processCommand(receivedMessage));
                for (String channel : group_channels) {
                    iMqttClient.get().subscribe(channel, (topic, receivedMessage) -> processCommand(receivedMessage));
                }
            }
        } catch (MqttException e) {
            iMqttClient = Optional.empty();
            log.warn(e.getMessage());
        }
    }

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
     * tell commander every 60 seconds, that I am alive.
     *
     * @throws SchedulerException
     */
    private void initHeartbeatJob() throws SchedulerException {
        JobDetail job = newJob(StatusJob.class)
                .withIdentity(myStatusJobKey)
                .build();

        Trigger trigger = newTrigger()
                .withIdentity(StatusJob.name + "-trigger", "group1")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1)
                        .repeatForever())
                .build();
        scheduler.scheduleJob(job, trigger);
    }

    /**
     * tell commander every 60 seconds, that I am alive.
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
                        .withIntervalInSeconds(5)
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
        //log.debug("received command {}", receivedMessage);

        try {
            final JSONObject cmds = new JSONObject(new String(receivedMessage.getPayload()));
            cmds.keySet().forEach(key -> {
                log.debug("handling command '{}' with {}", key, receivedMessage);
                switch (key.toLowerCase()) {
                    // example {"line_display": ['Rot: 123','Blau: 90']}
                    case "line_display": {
                        JSONArray cmdlist = cmds.getJSONArray("line_display");
                        // the first two lines are fixed for the agent (wifi and remaining time or score)
                        for (int line = 0; line < cmdlist.length(); line++) {
                            myLCD.setLine(lcdPage, line + 3, cmdlist.getString(line));
                        }
                        break;
                    }
                    case "matrix_display": {
                        JSONArray cmdlist = cmds.getJSONArray("matrix_display");
                        for (int line = 0; line < cmdlist.length(); line++) {
                            log.debug(cmdlist.getString(line));
                        }
                        break;
                    }
                    case "signal": {
                        final JSONObject signals = cmds.getJSONObject("signal");

                        // JSON Key:Values are unordered by definition. So we need to look first for led_all and sir_all.
                        Set<String> keys = signals.keySet();
                        if (keys.contains("led_all")) {
                            for (String led : Configs.ALL_LEDS) {
                                pinHandler.setScheme(led, signals.getString("led_all"));
                            }
                        }
                        if (keys.contains("sir_all")) {
                            for (String sirens : Configs.ALL_SIRENS) {
                                pinHandler.setScheme(sirens, signals.getString("sir_all"));
                            }
                        }
                        keys.remove("led_all");
                        keys.remove("sir_all");

                        // cycle through the rest of the signals - one by one
                        keys.forEach(signal_key -> {
                            pinHandler.setScheme(signal_key, signals.getString(signal_key));
                        });
                        break;
                    }
                    case "remaining": {
                        myLCD.setRemaining(cmds.getLong("remaining"));
                        break;
                    }
                    case "score": {
                        myLCD.setScore(Optional.of(cmds.getString("score")));
                        break;
                    }
                    case "init": { // remove all subscriptions
                        unsubscribe_from_extras();
                        break;
                    }
                    case "subscribe_to": {
                        try {
                            String additional_channel = String.format("%s/%s", TOPIC_PREFIX, cmds.getString(key));
                            if (!group_channels.contains(additional_channel)) {
                                iMqttClient.get().subscribe(additional_channel, (topic, msg) -> processCommand(msg));
                                group_channels.add(additional_channel);
                                log.debug("subscribing to {}", additional_channel);
                            }
                        } catch (MqttException e) {
                            log.warn(e);
                        }

                        break;
                    }
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

    private void unsubscribe_from_extras() {
        log.debug("removing all additional subscriptions");
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
                iMqttClient.get().publish(TOPIC_EVENT_FROM_ME, msg);
                log.info(TOPIC_EVENT_FROM_ME);
            } catch (MqttException mqe) {
                log.warn(mqe);
            }
        }
    }

    /**
     * switches the agent to the starting mode, when it doesnt know about a commander or a mqtt broker. Is called, when
     * the broker connection is lost.
     */
    private void waitForCommander() {
        if (iMqttClient.isPresent() && iMqttClient.get().isConnected()) {
            myLCD.setNetwork_lost(false);

            pinHandler.setScheme(Configs.OUT_LED_WHITE, "∞:on,350;off,3700");
            pinHandler.setScheme(Configs.OUT_LED_RED, "∞:off,350;on,350;off,3350");
            pinHandler.setScheme(Configs.OUT_LED_YELLOW, "∞:off,700;on,350;off,3000");
            pinHandler.setScheme(Configs.OUT_LED_GREEN, "∞:off,1050;on,350;off,2650");
            pinHandler.setScheme(Configs.OUT_LED_BLUE, "∞:off,1400;on,350;off,2300");

            myLCD.setLine(lcdPage, 3, "waiting for");
            myLCD.setLine(lcdPage, 4, "commander");

        } else {
            myLCD.setNetwork_lost(true);
            for (String led : Configs.ALL_LEDS) {
                pinHandler.setScheme(led, "off");
            }
            for (String siren : Configs.ALL_SIRENS) {
                pinHandler.setScheme(siren, "off");
            }
            myLCD.setLine(lcdPage, 3, "waiting for");
            myLCD.setLine(lcdPage, 4, "mqtt broker");

            // network status is always indicated with a flashing WHITE led
            pinHandler.setScheme(Configs.OUT_LED_WHITE, "∞:on,500;off,500");
            pinHandler.setScheme(Configs.OUT_LED_BLUE, "∞:off,500;on,500");

            // the wifi strength is reduced to good, fair, bad, none
            int wifi = me.getWifi();
            if (wifi > 0) pinHandler.setScheme(Configs.OUT_LED_RED, "∞:on,1000;off,1000");
            if (wifi > 1) pinHandler.setScheme(Configs.OUT_LED_YELLOW, "∞:on,1000;off,1000");
            if (wifi > 2) pinHandler.setScheme(Configs.OUT_LED_GREEN, "∞:on,1000;off,1000");
        }

    }

    /**
     * inform commander about my current status and what I am capable of (role) is repeated every 60 seconds by the
     * StatusJob
     */
    public void sendStatus() {
        me.setWifi_response_by_driver(Tools.getWifiDriverResponse(configs.get(Configs.WIFI_CMD_LINE)));
        myLCD.setWifi(me.getWifi_response_by_driver());
        //publishMessage(new JSONObject().put("status", me.toJson()).toString());
    }


    @SneakyThrows
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("connection to broker lost - {}", cause.getMessage());
        initMqttConnectionJob();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        log.debug("{} - {}", topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("{} - delivered", token.getMessageId());
    }

    public void shutdown() {
        iMqttClient.ifPresent(iMqttClient1 -> {
            try {
                iMqttClient1.disconnect();
                iMqttClient1.close();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });
        try {
            scheduler.shutdown();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // this will not happen, as we disabled the autoreconnect on the options
//        log.info("Connected to the broker @{}", iMqttClient.get().getServerURI());
//        if (reconnect) {
//
//            log.info("RE-Connected to the broker @{}", serverURI);
//            // we have to REsubscribe after a REconnect
//            try {
//                iMqttClient.get().subscribe(TOPIC_CMD_ME, (topic, receivedMessage) -> processCommand(receivedMessage));
//                iMqttClient.get().subscribe(TOPIC_CMD_ALL, (topic, receivedMessage) -> processCommand(receivedMessage));
//                //waitForCommander();
//            } catch (MqttException e) {
//                log.error(e);
//            }
//        }
    }
}

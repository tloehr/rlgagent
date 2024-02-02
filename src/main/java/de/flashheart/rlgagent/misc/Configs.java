package de.flashheart.rlgagent.misc;

import com.pi4j.io.gpio.RaspiPin;
import de.flashheart.rlgagent.Main;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Level;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;

@Log4j2
public class Configs extends AbstractConfigs {
    public static final String MQTT_BROKER = "mqtt_broker";
    public static final String MQTT_PORT = "mqtt_port";
    public static final String MQTT_ROOT = "mqtt_root";
    public static final String MQTT_RECONNECT = "mqtt_reconnect";
    public static final String MQTT_MAX_INFLIGHT = "mqtt_max_inflight";
    public static final String MQTT_TIMEOUT = "mqtt_timeout";
    public static final String MQTT_CLEAN_SESSION = "mqtt_clean_session";
    public static final String MQTT_QOS = "mqtt_qos";
    public static final String MQTT_RETAINED = "mqtt_retained";
    public static final String OUT_LED_WHITE = "wht";
    public static final String OUT_LED_RED = "red";
    public static final String OUT_LED_BLUE = "blu";
    public static final String OUT_LED_YELLOW = "ylw";
    public static final String OUT_LED_GREEN = "grn";
    public static final String OUT_SIREN1 = "sir1";
    public static final String OUT_SIREN2 = "sir2";
    public static final String OUT_SIREN3 = "sir3";
    public static final String OUT_SIREN4 = "sir4";
    public static final String TRIGGER_ON_HIGH_SIREN1 = "trigger_on_high_sir1";
    public static final String TRIGGER_ON_HIGH_SIREN2 = "trigger_on_high_sir2";
    public static final String TRIGGER_ON_HIGH_SIREN3 = "trigger_on_high_sir3";
    public static final String TRIGGER_ON_HIGH_SIREN4 = "trigger_on_high_sir4";
    public static final String OUT_BUZZER = "buzzer"; // ein einfacher Buzzer
    public static final String IN_BTN01 = "btn01";
    public static final String IN_BTN02 = "btn02";
    public static final String BUTTON_DEBOUNCE = "button_debounce";
    public static final String LCD_I2C_ADDRESS = "lcd_i2c_address";
    public static final String LCD_ROWS = "lcd_rows";
    public static final String LCD_COLS = "lcd_cols";
    public static final String MCP23017_I2C_ADDRESS = "mcp23017_i2c_address";
    //    public static final String LOGLEVEL = "loglevel";
    public static final String[] ALL_LEDS = new String[]{OUT_LED_WHITE, OUT_LED_RED, OUT_LED_YELLOW, OUT_LED_GREEN, OUT_LED_BLUE};
    public static final String[] ALL_SIRENS = new String[]{OUT_SIREN1, OUT_SIREN2, OUT_SIREN3, OUT_SIREN4, OUT_BUZZER};
    public static final String[] ALL_PINS = ArrayUtils.addAll(ALL_LEDS, ALL_SIRENS);
    public static final String WIFI_CMD_LINE = "wifi_cmd";
    public static final String IP_CMD_LINE = "ip_address_cmd";
    public static final String MPG321_BIN = "mpg321_bin";
    public static final String MPG321_OPTIONS = "mpg321_options";
    public static final String MY_ID = "myid";
    public static final String PING_TRIES = "ping_tries";
    public static final String PING_TIMEOUT = "ping_timeout";
    public static final String FRAME_LOCATION_X = "frameLocationX";
    public static final String FRAME_LOCATION_Y = "frameLocationY";
    public static final String SELECTED_TAB = "selected_tab";
    public static final String FRAME_WIDTH0 = "frameWidth0";
    public static final String FRAME_HEIGHT0 = "frameHeight0";
    public static final String FRAME_WIDTH1 = "frameWidth1";
    public static final String FRAME_HEIGHT1 = "frameHeight1";
    public static final String FRAME_WIDTH2 = "frameWidth2";
    public static final String FRAME_HEIGHT2 = "frameHeight2";
    public static final String VISUALS = Configs.OUT_LED_WHITE +
            "|" + Configs.OUT_LED_RED +
            "|" + Configs.OUT_LED_YELLOW +
            "|" + Configs.OUT_LED_GREEN +
            "|" + Configs.OUT_LED_BLUE;
    public static final String ACOUSTICS = Configs.OUT_SIREN1 +
            "|" + Configs.OUT_SIREN2 +
            "|" + Configs.OUT_SIREN3 +
            "|" + Configs.OUT_SIREN4 +
            "|" + Configs.OUT_BUZZER;
    public static final String NETWORKING_MONITOR_INTERVAL_IN_SECONDS = "network_monitor_interval_in_seconds";
    public static final String STATUS_INTERVAL_IN_SECONDS = "status_interval_in_seconds";
    public static final String NETWORKING_MONITOR_DISCONNECT_AFTER_FAILED_PINGS = "networking_monitor_disconnect_after_failed_pings";

//    public Properties get_blink_schemes() {
//        return blink_schemes;
//    }

//    private final Properties blink_schemes;
    private final JSONObject scheme_macros;


    public Configs() throws IOException {
        super(System.getProperties().getProperty("workspace"));
        // predefined schemes belong to the config file
        // so we can change the commander without necessarily having to update the agent, too
        scheme_macros = new JSONObject(IOUtils.toString(this.getClass().getClassLoader().getResourceAsStream("scheme_macros.json"), StandardCharsets.UTF_8));
        log.debug("breakpoint");
    }


    // for fixed blinking schemes
//    public String getScheme(String key) {
//        return blink_schemes.getProperty(key, key);
//    }

    public JSONObject getScheme_macros() {
        return scheme_macros;
    }

    public Optional<File> getAudioFile(String subpath, String filename) {
        String filesep = subpath.isEmpty() ? "" : File.separator;
        File file = new File(getWORKSPACE() + File.separator + "audio" + File.separator + subpath + filesep + filename + ".mp3");
        return file.exists() ? Optional.of(file) : Optional.empty();
    }

    /**
     * picks a random file in a given subpath on the WORKSPACE.
     *
     * @param subpath
     * @return
     */
    public Optional<File> pickRandomAudioFile(String subpath) {
        Optional<File> chosen;
        String filesep = subpath.isEmpty() ? "" : File.separator;
        try {
            // pick a random file from that list.
            File[] files = new File(getWORKSPACE() + File.separator + "audio" + filesep + subpath).listFiles((dir, name) -> name.endsWith("mp3"));
            Random r = new Random();
            chosen = Optional.of(files[r.nextInt(files.length)]);
        } catch (Exception e) {
            chosen = Optional.empty();
        }
        return chosen;
    }


    public String getAgentname() {
        return configs.getProperty(MY_ID);
    }

    @Override
    public String getProjectName() {
        return Main.PROJECT;
    }

    /**
     * Das System erwartet dass es zu jedem Schlüssel einen Wert gibt. Die Defaults verhindern dass NPEs bei neuen
     * Installationen auftreten.
     */
    @Override
    public void loadDefaults() {
        configs.setProperty(MQTT_BROKER, "localhost");
        configs.setProperty(MY_ID, "ag01");
        configs.setProperty(MQTT_PORT, "1883");
        configs.setProperty(MQTT_ROOT, "rlg");
        configs.setProperty(MQTT_MAX_INFLIGHT, "1000");
        configs.setProperty(MQTT_CLEAN_SESSION, "true");
        configs.setProperty(MQTT_TIMEOUT, "10");
        configs.setProperty(MQTT_RECONNECT, "true");
        configs.setProperty(MQTT_QOS, "2");
        configs.setProperty(MQTT_RETAINED, "false");
        configs.setProperty(PING_TRIES, "1");
        configs.setProperty(PING_TIMEOUT, "500");
        configs.setProperty(NETWORKING_MONITOR_INTERVAL_IN_SECONDS, "10");
        configs.setProperty(NETWORKING_MONITOR_DISCONNECT_AFTER_FAILED_PINGS, "3");
        configs.setProperty(STATUS_INTERVAL_IN_SECONDS, "60");

        configs.setProperty(LCD_I2C_ADDRESS, "0x27");
        configs.setProperty(MCP23017_I2C_ADDRESS, "0x20");
        configs.setProperty(BUTTON_DEBOUNCE, "200");

        configs.setProperty(LCD_COLS, "20");
        configs.setProperty(LCD_ROWS, "4");

        configs.setProperty(LOGLEVEL, Level.DEBUG.name());

        // für RLG Agent Board 1.0
        configs.setProperty(OUT_LED_WHITE, RaspiPin.GPIO_02.getName()); // 13
        configs.setProperty(OUT_LED_RED, RaspiPin.GPIO_01.getName()); // 12
        configs.setProperty(OUT_LED_YELLOW, RaspiPin.GPIO_05.getName()); // 18
        configs.setProperty(OUT_LED_GREEN, RaspiPin.GPIO_21.getName()); // 29
        configs.setProperty(OUT_LED_BLUE, RaspiPin.GPIO_22.getName()); // 31
        configs.setProperty(OUT_BUZZER, RaspiPin.GPIO_26.getName()); // 32

        configs.setProperty(OUT_SIREN1, RaspiPin.GPIO_07.getName()); // 7
        configs.setProperty(OUT_SIREN2, RaspiPin.GPIO_00.getName()); // 11
        configs.setProperty(OUT_SIREN3, RaspiPin.GPIO_06.getName()); // 22
        configs.setProperty(OUT_SIREN4, RaspiPin.GPIO_29.getName()); // 33

        configs.setProperty(TRIGGER_ON_HIGH_SIREN1, "true");
        configs.setProperty(TRIGGER_ON_HIGH_SIREN2, "true");
        configs.setProperty(TRIGGER_ON_HIGH_SIREN3, "true");
        configs.setProperty(TRIGGER_ON_HIGH_SIREN4, "true");

        configs.setProperty(IN_BTN01, RaspiPin.GPIO_03.getName()); // 15
        configs.setProperty(IN_BTN02, RaspiPin.GPIO_04.getName()); // 16

        configs.setProperty(WIFI_CMD_LINE, "iwconfig wlan0");
        configs.setProperty(IP_CMD_LINE, "ip a s wlan0|grep -i 'inet '|awk '{print $2}'");
        configs.setProperty(MPG321_BIN, "");
        configs.setProperty(MPG321_OPTIONS, "");

        configs.setProperty(FRAME_LOCATION_X, "0");
        configs.setProperty(FRAME_LOCATION_Y, "0");
        configs.setProperty(SELECTED_TAB, "0");
        configs.setProperty(FRAME_WIDTH0, "275");
        configs.setProperty(FRAME_HEIGHT0, "382");
        configs.setProperty(FRAME_WIDTH1, "105");
        configs.setProperty(FRAME_HEIGHT1, "592");
        configs.setProperty(FRAME_WIDTH2, "192");
        configs.setProperty(FRAME_HEIGHT2, "420");


    }

}

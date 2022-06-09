package de.flashheart.rlgagent.misc;

import com.pi4j.io.gpio.RaspiPin;
import de.flashheart.rlgagent.Main;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Level;

import javax.swing.text.html.Option;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;

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
    public static final String OUT_LED_WHITE = "led_wht";
    public static final String OUT_LED_RED = "led_red";
    public static final String OUT_LED_BLUE = "led_blu";
    public static final String OUT_LED_YELLOW = "led_ylw";
    public static final String OUT_LED_GREEN = "led_grn";
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
    public static final String LCD_I2C_ADDRESS = "lcd_i2c_address";
    public static final String LCD_ROWS = "lcd_rows";
    public static final String LCD_COLS = "lcd_cols";
    public static final String MCP23017_I2C_ADDRESS = "mcp23017_i2c_address";
    public static final String LOGLEVEL = "loglevel";
    public static final String[] ALL_LEDS = new String[]{OUT_LED_WHITE, OUT_LED_RED, OUT_LED_YELLOW, OUT_LED_GREEN, OUT_LED_BLUE};
    public static final String[] ALL_SIRENS = new String[]{OUT_SIREN1, OUT_SIREN2, OUT_SIREN3, OUT_SIREN4};
    public static final String[] ALL = ArrayUtils.addAll(ALL_LEDS, ALL_SIRENS);
    public static final String WIFI_CMD_LINE = "wifi_cmd";
    public static final String MPG321_BIN = "mpg321_bin";
    public static final String MY_ID = "myid";
    protected final Properties blink_schemes;

    public Configs() throws IOException {
        super(System.getProperties().getProperty("workspace"));
        // predefined schemes belong to the config file
        // so we can change the commander without necessarily having to update the agent, too

        blink_schemes = new Properties();

        blink_schemes.setProperty("very_long", "1:on,5000;off,1");
        blink_schemes.setProperty("long", "1:on,2500;off,1");
        blink_schemes.setProperty("medium", "1:on,1000;off,1");
        blink_schemes.setProperty("short", "1:on,500;off,1");
        blink_schemes.setProperty("very_short", "1:on,250;off,1");

        // recurring signals
        blink_schemes.setProperty("very_slow", "infty:on,1000;off,5000");
        blink_schemes.setProperty("slow", "infty:on,1000;off,2000");
        blink_schemes.setProperty("normal", "infty:on,1000;off,1000");
        blink_schemes.setProperty("fast", "infty:on,500;off,500");
        blink_schemes.setProperty("very_fast", "infty:on,250;off,250");
        blink_schemes.setProperty("netstatus", "infty:on,250;off,750");


        // for sirens
        blink_schemes.setProperty("single_buzz", "1:on,75;off,75");
        blink_schemes.setProperty("double_buzz", "2:on,75;off,75");
        blink_schemes.setProperty("triple_buzz", "3:on,75;off,75");
    }


    // for fixed blinking schemes
    public String getScheme(String key) {
        return blink_schemes.getProperty(key, key);
    }

    public Optional<File> getAudioFile(String filename) {
        File file = new File(getWORKSPACE() + File.separator + "audio" + File.separator + "intro" + File.separator + filename);
        return file.exists() ? Optional.of(file) : Optional.empty();
    }

    /**
     * picks a random file in a given subpath on the WORKSPACE.
     *
     * @param subpath
     * @return
     */
    public Optional<File> pickRandomFile(String subpath) {
        Optional<File> chosen;
        try {
            // pick a random file from that list.
            File[] files = new File(getWORKSPACE() + File.separator + subpath).listFiles((dir, name) -> name.endsWith("mp3"));
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
        configs.setProperty(MQTT_QOS, "1"); // QOS1 is good enough - QOS2 can cause some lag
        configs.setProperty(MQTT_RETAINED, "true");

        configs.setProperty(LCD_I2C_ADDRESS, "0x27");
        configs.setProperty(MCP23017_I2C_ADDRESS, "0x20");

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
        configs.setProperty(OUT_SIREN4, RaspiPin.GPIO_23.getName()); // 33

        configs.setProperty(TRIGGER_ON_HIGH_SIREN1, "true");
        configs.setProperty(TRIGGER_ON_HIGH_SIREN2, "true");
        configs.setProperty(TRIGGER_ON_HIGH_SIREN3, "true");
        configs.setProperty(TRIGGER_ON_HIGH_SIREN4, "true");

        configs.setProperty(IN_BTN01, RaspiPin.GPIO_03.getName()); // 15
        configs.setProperty(IN_BTN02, RaspiPin.GPIO_04.getName()); // 16

        configs.setProperty(WIFI_CMD_LINE, "iwconfig wlan1");
        configs.setProperty(MPG321_BIN, "");


        // missionbox
//        configs.setProperty(OUT_LED_WHITE, RaspiPin.GPIO_05.getName()); // 13
//        configs.setProperty(OUT_LED_RED, RaspiPin.GPIO_06.getName()); // 12
//        configs.setProperty(OUT_LED_YELLOW, RaspiPin.GPIO_10.getName()); // 18
//        configs.setProperty(OUT_LED_GREEN, RaspiPin.GPIO_27.getName()); // 29
//        configs.setProperty(OUT_LED_BLUE, RaspiPin.GPIO_28.getName()); // 31
//        configs.setProperty(OUT_BUZZER, RaspiPin.GPIO_29.getName()); // 32
//
//        configs.setProperty(OUT_SIREN1, RaspiPin.GPIO_00.getName()); // 7
//        configs.setProperty(OUT_SIREN2, RaspiPin.GPIO_02.getName()); // 11
//        configs.setProperty(OUT_SIREN3, RaspiPin.GPIO_03.getName()); // 22
//
//        configs.setProperty(IN_BTN01, RaspiPin.GPIO_15.getName()); // 15
//        configs.setProperty(IN_BTN02, RaspiPin.GPIO_16.getName()); // 16


        // für RLG Mainboard 1.0
//        configs.setProperty(OUT_LED_WHITE, MCP23017Pin.GPIO_B5.getName()); // 11
//        configs.setProperty(OUT_LED_RED, MCP23017Pin.GPIO_B0.getName()); // 13
//        configs.setProperty(OUT_LED_YELLOW, MCP23017Pin.GPIO_B4.getName()); // 15
//        configs.setProperty(OUT_LED_GREEN, MCP23017Pin.GPIO_B3.getName()); // 4
//        configs.setProperty(OUT_LED_BLUE, MCP23017Pin.GPIO_B1.getName()); // 5
        //configs.setProperty(OUT_BUZZER, MCP23017Pin.GPIO_A6.getName()); // 6


    }

}

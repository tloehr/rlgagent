package de.flashheart.rlgagent.misc;

import com.pi4j.io.gpio.RaspiPin;
import de.flashheart.rlgagent.Main;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.IOException;

public class Configs extends AbstractConfigs {
    public static final String MQTT_BROKER = "mqtt_broker";
    public static final String MQTT_PORT = "mqtt_port";
    public static final String MQTT_ROOT = "mqtt_root";
    public static final String OUT_LED_WHITE = "led_wht";
    public static final String OUT_LED_RED = "led_red";
    public static final String OUT_LED_BLUE = "led_blu";
    public static final String OUT_LED_YELLOW = "led_ylw";
    public static final String OUT_LED_GREEN = "led_grn";
    public static final String OUT_SIREN1 = "sir1";
    public static final String OUT_SIREN2 = "sir2";
    public static final String OUT_SIREN3 = "sir3";
    public static final String OUT_BUZZER = "buzzer"; // ein einfacher Buzzer
    public static final String IN_BTN01 = "btn01";
    public static final String IN_BTN02 = "btn02";
    public static final String LCD_I2C_ADDRESS = "lcd_i2c_address";
    public static final String LCD_ROWS = "lcd_rows";
    public static final String LCD_COLS = "lcd_cols";
    public static final String MCP23017_I2C_ADDRESS = "mcp23017_i2c_address";
    public static final String LOGLEVEL = "loglevel";
    public static final String[] ALL_LEDS = new String[]{OUT_LED_WHITE, OUT_LED_RED, OUT_LED_YELLOW, OUT_LED_GREEN, OUT_LED_BLUE};
    public static final String[] ALL_SIRENS = new String[]{OUT_SIREN1, OUT_SIREN2, OUT_SIREN3};
    public static final String[] ALL = ArrayUtils.addAll(ALL_LEDS, ALL_SIRENS);
    public static final String WIFI_CMD_LINE = "wifi_cmd";
    private final String agentname;

    public Configs(String agentname) throws IOException {
        super(System.getProperty("user.home")+ File.separator+agentname);
        this.agentname = agentname;
    }

    public String getAgentname() {
        return agentname;
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
        configs.setProperty(MQTT_PORT, "1883");
        // configs.setProperty(MQTT_BROKER, String.format("tcp://%s:%s", "mqtt", "port"));
        configs.setProperty(MQTT_ROOT, "rlg");

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

        configs.setProperty(IN_BTN01, RaspiPin.GPIO_03.getName()); // 15
        configs.setProperty(IN_BTN02, RaspiPin.GPIO_04.getName()); // 16

        configs.setProperty(WIFI_CMD_LINE, "iwconfig wlan1|egrep \"Signal level\"|awk '{print $4}'");

        // predefined schemes belong to the config file
        // so we can change the commander without necessarily having to update the agent, too
        configs.setProperty("very_long", "1:on,5000;off,1");
        configs.setProperty("long", "1:on,2500;off,1");
        configs.setProperty("medium", "1:on,1000;off,1");
        configs.setProperty("short", "1:on,500;off,1");

        // recurring signals
        configs.setProperty("slow", "∞:on,2000;off,1000");
        configs.setProperty("normal", "∞:on,1000;off,1000");
        configs.setProperty("fast", "∞:on,500;off,500");
        configs.setProperty("very_fast", "∞:on,250;off,250");

        // for sirens
        configs.setProperty("single_buzz", "1:on,75;off,75");
        configs.setProperty("double_buzz", "2:on,75;off,75");
        configs.setProperty("triple_buzz", "3:on,75;off,75");

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

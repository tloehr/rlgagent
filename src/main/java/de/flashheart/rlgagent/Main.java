package de.flashheart.rlgagent;

import com.bulenkov.darcula.DarculaLaf;
import com.pi4j.gpio.extension.mcp.MCP23017GpioProvider;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import de.flashheart.rlgagent.hardware.I2CLCD;
import de.flashheart.rlgagent.hardware.abstraction.MyLCD;
import de.flashheart.rlgagent.hardware.abstraction.MyPin;
import de.flashheart.rlgagent.hardware.pinhandler.PinHandler;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.Tools;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

import javax.swing.*;
import javax.swing.plaf.basic.BasicLookAndFeel;
import java.awt.*;
import java.io.IOException;
import java.util.Optional;

@Log4j2
/**
 * execute: mvn package exec:java -Dworkspace=/Users/tloehr/ag01
 */
public class Main {
    public static final String PROJECT = "rlgagent";
    private static RLGAgent agent;
    private static Configs configs;
    private static PinHandler pinHandler;
    private static MyLCD myLCD;
    private static Optional<MyUI> myUI;

    private static Optional<GpioController> gpioController;
    private static Optional<MCP23017GpioProvider> mcp23017_1;
    private static Optional<I2CBus> i2CBus;
    private static Optional<I2CLCD> lcd_hardware;


    public static void main(String[] args) throws Exception {
        initBaseSystem(args);
        initHardware();
        initGameSystem();
        agent = new RLGAgent(configs, myUI, gpioController, pinHandler, myLCD);
    }

    private static void initBaseSystem(String[] args) throws IOException, UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        if (!System.getProperties().containsKey("workspace")) {
            log.fatal("workspace directory parameter needs to be set via -Dworkspace=/path/you/want");
            Runtime.getRuntime().halt(0);
        }

        Options opts = new Options();
        opts.addOption("h", "help", false, "show help");
        DefaultParser parser = new DefaultParser();
        CommandLine cl = null;
        String footer = "https://www.flashheart.de";

        String jarname = new java.io.File(RLGAgent.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();

        try {
            cl = parser.parse(opts, args);
        } catch (ParseException ex) {
            HelpFormatter f = new HelpFormatter();
            f.printHelp(jarname + ".jar [OPTION]", PROJECT, opts, footer);
            Runtime.getRuntime().halt(0);
        }

        if (cl.hasOption("h")) {
            HelpFormatter f = new HelpFormatter();
            f.printHelp(jarname + ".jar [OPTION]", PROJECT, opts, footer);
            Runtime.getRuntime().halt(0);
        }

        configs = new Configs();

        //Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.getLevel(configs.get(Configs.LOGLEVEL)));
        pinHandler = new PinHandler(configs);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> agent.procShutdown(false)));

        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        myUI = GraphicsEnvironment.isHeadless() ? Optional.empty() : Optional.of(new MyUI(configs.getAgentname()));
        myUI.ifPresent(myUI1 -> myUI1.setVisible(true));
    }

    private static void initGameSystem() {
        pinHandler.add(new MyPin(Configs.OUT_LED_WHITE, configs, myUI, gpioController, mcp23017_1, -1, -1));
        pinHandler.add(new MyPin(Configs.OUT_LED_RED, configs, myUI, gpioController, mcp23017_1, -1, -1));
        pinHandler.add(new MyPin(Configs.OUT_LED_YELLOW, configs, myUI, gpioController, mcp23017_1, -1, -1));
        pinHandler.add(new MyPin(Configs.OUT_LED_GREEN, configs, myUI, gpioController, mcp23017_1, -1, -1));
        pinHandler.add(new MyPin(Configs.OUT_LED_BLUE, configs, myUI, gpioController, mcp23017_1, -1, -1));
        // sirens
        pinHandler.add(new MyPin(Configs.OUT_SIREN1, configs, myUI, gpioController, mcp23017_1, 70, 60));
        pinHandler.add(new MyPin(Configs.OUT_SIREN2, configs, myUI, gpioController, mcp23017_1, 50, 90));
        pinHandler.add(new MyPin(Configs.OUT_SIREN3, configs, myUI, gpioController, mcp23017_1, 50, 75));
        pinHandler.add(new MyPin(Configs.OUT_BUZZER, configs, myUI, gpioController, mcp23017_1, 70, 30));

    }

    /**
     * we are working with 4 hardware abstractions here. they are all Optionals, because the agent may not be
     * necessarily running on a Raspi.
     * <ul>
     *     <li>gpioController</li>
     *     <li>i2CBus</li>
     *     <li>lcd_hardware</li>
     *     <li>mcp23017_1</li> - not currently in use by the rlgagent hat. "but we could if we wanted to"
     * </ul>
     */
    private static void initHardware() {
        gpioController = Optional.empty();
        i2CBus = Optional.empty();
        lcd_hardware = Optional.empty();
        mcp23017_1 = Optional.empty();

        if (Tools.isArm()) {
            gpioController = Optional.of(GpioFactory.getInstance());
            try {
                i2CBus = Optional.of(I2CFactory.getInstance(I2CBus.BUS_1));
            } catch (I2CFactory.UnsupportedBusNumberException | IOException e) {
                log.warn(e);
                i2CBus = Optional.empty();
            }

            // is a LCD available ?
            i2CBus.ifPresent(i2CBus1 -> {
                try {
                    I2CDevice device = i2CBus.get().getDevice(Integer.decode(configs.get(Configs.LCD_I2C_ADDRESS)));
                    device.read(); // to make sure the device is available. Will produce an Exception otherwise.
                    lcd_hardware = Optional.of(new I2CLCD(device));
                    lcd_hardware.get().init();
                    lcd_hardware.get().backlight(true);
                } catch (IOException e) {
                    log.warn(e);
                    lcd_hardware = Optional.empty();
                }
            });

            // is a MCP23017 available ?
            i2CBus.ifPresent(i2CBus1 -> {
                try {
                    mcp23017_1 = Optional.of(new MCP23017GpioProvider(i2CBus.get(), Integer.decode(configs.get(Configs.MCP23017_I2C_ADDRESS))));
                } catch (IOException e) {
                    log.warn(e);
                    mcp23017_1 = Optional.empty();
                }
            });
        }

        myLCD = new MyLCD(myUI, configs, lcd_hardware);
    }


}

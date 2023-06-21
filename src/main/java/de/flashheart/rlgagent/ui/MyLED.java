package de.flashheart.rlgagent.ui;

import de.flashheart.rlgagent.misc.Configs;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Created by tloehr on 16.03.16.
 */
public class MyLED extends JLabel {
    private Icon imageOn;
    private Icon imageOff;

    public final String icon_ledOrangeOn = "ledorange.png";
    public final String icon_ledOrangeOff = "leddarkorange.png";
    public final String icon_ledPurpleOff = "leddarkpurple.png";
    public final String icon_ledPurpleOn = "ledpurple.png";
    public final String icon_ledBlueOff = "led-blue-off.png";
    public final String icon_ledBlueOn = "led-blue-on.png";
    public final String icon_ledGreenOff = "led-green-off.png";
    public final String icon_ledGreenOn = "led-green-on.png";
    public final String icon_ledYellowOff = "led-yellow-off.png";
    public final String icon_ledYellowOn = "led-yellow-on.png";
    public final String icon_ledRedOff = "led-red-off.png";
    public final String icon_ledRedOn = "led-red-on.png";
    public final String icon_ledWhiteOff = "led-white-off.png";
    public final String icon_ledWhiteOn = "led-white-on.png";
    public final String iconSpeakerOn = "speaker-on.png";
    public final String iconSpeakerOff = "speaker-off.png";

    private Color color;
    private boolean state;
    private String size = "22x22";

//    public MyLED() {
//        this(null, Color.WHITE);
//    }

    public MyLED(String text, Color color) {
        super(text);
        this.color = color;
        setText(text);
        setColor();
        setState(false);
        setSize(getWidth(), 22);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    @Override
    public void setText(String text) {
        if (Arrays.stream(Configs.ALL_LEDS).anyMatch(s -> s.equals(text))) {
            setToolTipText(text); // kein Platz auf dem Bildschirm für LED Beschriftungen
        } else {
            super.setText(text);
        }
    }

    public void setIconSize(int size) {
        this.size = size + "x" + size;
        SwingUtilities.invokeLater(() -> {
            setColor();
            setState(state);
            revalidate();
            repaint();
        });
    }

    public Color getColor() {
        return color;
    }

    private Icon get_icon(String icon_string) {
        return new ImageIcon(getClass().getResource("/artwork/" + size + "/" + icon_string));
    }

    private void setColor() {
        if (color.equals(Color.WHITE)) {
            imageOn = get_icon(icon_ledWhiteOn);
            imageOff = get_icon(icon_ledWhiteOff);
            return;
        }
        if (color.equals(Color.BLUE)) {
            imageOn = get_icon(icon_ledBlueOn);
            imageOff = get_icon(icon_ledBlueOff);
            return;
        }
        if (color.equals(Color.RED)) {
            imageOn = get_icon(icon_ledRedOn);
            imageOff = get_icon(icon_ledRedOff);
            return;
        }
        if (color.equals(Color.YELLOW)) {
            imageOn = get_icon(icon_ledYellowOn);
            imageOff = get_icon(icon_ledYellowOff);
            return;
        }
        if (color.equals(Color.GREEN)) {
            imageOn = get_icon(icon_ledGreenOn);
            imageOff = get_icon(icon_ledGreenOff);
            return;
        }
        if (color.equals(Color.ORANGE)) {
            imageOn = get_icon(icon_ledOrangeOn);
            imageOff = get_icon(icon_ledOrangeOff);
            return;
        }
        if (color.equals(Color.MAGENTA)) {
            imageOn = get_icon(icon_ledPurpleOn);
            imageOff = get_icon(icon_ledPurpleOff);
            return;
        }
        if (color.equals(Color.DARK_GRAY)) { // kleiner trick.
            imageOn = get_icon(iconSpeakerOn);
            imageOff = get_icon(iconSpeakerOff);
            return;
        }
    }

    public void setState(boolean on) {
        this.state = on;
        SwingUtilities.invokeLater(() -> {
            setIcon(on ? imageOn : imageOff);
            revalidate();
            repaint();
        });
    }

    public boolean isState() {
        return state;
    }
}

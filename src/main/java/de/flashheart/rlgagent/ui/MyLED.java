package de.flashheart.rlgagent.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Created by tloehr on 16.03.16.
 */
public class MyLED extends JLabel {
    private Icon imageOn;
    private Icon imageOff;

    public final Icon icon22ledOrangeOn = new ImageIcon(getClass().getResource("/artwork/22x22/ledorange.png"));
    public final Icon icon22ledOrangeOff = new ImageIcon(getClass().getResource("/artwork/22x22/leddarkorange.png"));
    public final Icon icon22ledPurpleOff = new ImageIcon(getClass().getResource("/artwork/22x22/leddarkpurple.png"));
    public final Icon icon22ledPurpleOn = new ImageIcon(getClass().getResource("/artwork/22x22/ledpurple.png"));
    public final Icon icon22ledBlueOff = new ImageIcon(getClass().getResource("/artwork/48x48/led-blue-off.png"));
    public final Icon icon22ledBlueOn = new ImageIcon(getClass().getResource("/artwork/48x48/led-blue-on.png"));
    public final Icon icon22ledGreenOff = new ImageIcon(getClass().getResource("/artwork/48x48/led-green-off.png"));
    public final Icon icon22ledGreenOn = new ImageIcon(getClass().getResource("/artwork/48x48/led-green-on.png"));
    public final Icon icon22ledYellowOff = new ImageIcon(getClass().getResource("/artwork/48x48/led-yellow-off.png"));
    public final Icon icon22ledYellowOn = new ImageIcon(getClass().getResource("/artwork/48x48/led-yellow-on.png"));
    public final Icon icon22ledRedOff = new ImageIcon(getClass().getResource("/artwork/48x48/led-red-off.png"));
    public final Icon icon22ledRedOn = new ImageIcon(getClass().getResource("/artwork/48x48/led-red-on.png"));
    public final Icon icon22ledWhiteOff = new ImageIcon(getClass().getResource("/artwork/48x48/led-white-off.png"));
    public final Icon icon22ledWhiteOn = new ImageIcon(getClass().getResource("/artwork/48x48/led-white-on.png"));
    public final Icon iconSpeakerOn = new ImageIcon(getClass().getResource("/artwork/48x48/speaker-on.png"));
    public final Icon iconSpeakerOff = new ImageIcon(getClass().getResource("/artwork/48x48/speaker-off.png"));

    private Color color;
    private boolean state;

    public MyLED() {
        this(null, Color.WHITE);
    }

    public MyLED(String text, Color color) {
        super(text);
        this.color = color;
        super.setText(text);
        setColor(color);
        setState(false);
    }

    @Override
    public void setText(String text) {
        setToolTipText(text); // kein Platz auf dem Bildschirm
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        if (color.equals(Color.WHITE)) {
            imageOn = icon22ledWhiteOn;
            imageOff = icon22ledWhiteOff;
            return;
        }
        if (color.equals(Color.BLUE)) {
            imageOn = icon22ledBlueOn;
            imageOff = icon22ledBlueOff;
            return;
        }
        if (color.equals(Color.RED)) {
            imageOn = icon22ledRedOn;
            imageOff = icon22ledRedOff;
            return;
        }
        if (color.equals(Color.YELLOW)) {
            imageOn = icon22ledYellowOn;
            imageOff = icon22ledYellowOff;
            return;
        }
        if (color.equals(Color.GREEN)) {
            imageOn = icon22ledGreenOn;
            imageOff = icon22ledGreenOff;
            return;
        }
        if (color.equals(Color.ORANGE)) {
            imageOn = icon22ledOrangeOn;
            imageOff = icon22ledOrangeOff;
            return;
        }
        if (color.equals(Color.MAGENTA)) {
            imageOn = icon22ledPurpleOn;
            imageOff = icon22ledPurpleOff;
            return;
        }
        if (color.equals(Color.DARK_GRAY)) { // kleiner trick.
            imageOn = iconSpeakerOn;
            imageOff = iconSpeakerOff;
            return;
        }
    }

    public void setState(boolean on) {
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

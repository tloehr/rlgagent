/*
 * Created by JFormDesigner on Mon Aug 23 15:29:20 CEST 2021
 */

package de.flashheart.rlgagent.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import de.flashheart.rlgagent.misc.Configs;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * @author Torsten Löhr
 */
@Log4j2
public class MyUI extends JFrame {
    HashMap<String, MyLED> pinMap;
    ArrayList<JLabel> lineList;

    public MyUI(String title) {

        //Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.getLevel(configs.get(Configs.LOGLEVEL)));
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            FlatDarkLaf.setup();
        } catch (Exception ex) {
            log.fatal("Failed to initialize LaF");
            System.exit(0);
        }
        initComponents();
        setTitle(title);
        initLEDs();
        initLCD();
    }

    private void initLCD() {
        lineList = new ArrayList<>();
    }

    private void initLEDs() {
        pinMap = new HashMap<>();
        pinMap.put(Configs.OUT_LED_RED, new MyLED(Configs.OUT_LED_RED, Color.RED));
        pinMap.put(Configs.OUT_LED_YELLOW, new MyLED(Configs.OUT_LED_YELLOW, Color.YELLOW));
        pinMap.put(Configs.OUT_LED_GREEN, new MyLED(Configs.OUT_LED_GREEN, Color.GREEN));
        pinMap.put(Configs.OUT_LED_BLUE, new MyLED(Configs.OUT_LED_BLUE, Color.BLUE));
        pinMap.put(Configs.OUT_LED_WHITE, new MyLED(Configs.OUT_LED_WHITE, Color.WHITE));
        pinMap.put(Configs.OUT_SIREN1, new MyLED(Configs.OUT_SIREN1, Color.DARK_GRAY));
        pinMap.put(Configs.OUT_SIREN2, new MyLED(Configs.OUT_SIREN2, Color.DARK_GRAY));
        pinMap.put(Configs.OUT_SIREN3, new MyLED(Configs.OUT_SIREN3, Color.DARK_GRAY));
        pinMap.put(Configs.OUT_SIREN4, new MyLED(Configs.OUT_SIREN4, Color.DARK_GRAY));
        pinMap.put(Configs.OUT_BUZZER, new MyLED(Configs.OUT_BUZZER, Color.DARK_GRAY));

        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.LINE_AXIS));

        JPanel leds = new JPanel();
        leds.setLayout(new BoxLayout(leds, BoxLayout.PAGE_AXIS));
        JPanel sirens = new JPanel();
        sirens.setLayout(new BoxLayout(sirens, BoxLayout.PAGE_AXIS));

        page.add(leds);
        page.add(Box.createRigidArea(new Dimension(20, 0)));
        page.add(sirens);

        leds.add(pinMap.get(Configs.OUT_LED_WHITE));
        leds.add(pinMap.get(Configs.OUT_LED_RED));
        leds.add(pinMap.get(Configs.OUT_LED_YELLOW));
        leds.add(pinMap.get(Configs.OUT_LED_GREEN));
        leds.add(pinMap.get(Configs.OUT_LED_BLUE));
        sirens.add(pinMap.get(Configs.OUT_SIREN1));
        sirens.add(pinMap.get(Configs.OUT_SIREN2));
        sirens.add(pinMap.get(Configs.OUT_SIREN3));
        sirens.add(pinMap.get(Configs.OUT_SIREN4));
        sirens.add(pinMap.get(Configs.OUT_BUZZER));
        content1.add(page, BorderLayout.CENTER);
    }

    public void setState(String name, boolean on) {
        pinMap.get(name).setState(on);
    }

    public void addLCDLine(int cols) {
        JLabel lbl = new JLabel(StringUtils.repeat(" ", cols));
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 16));
        lbl.setForeground(Color.BLACK);
        lineList.add(lbl);
        pnlLCD.add(lbl);
    }

    public void setLine(int line, String text) {
        SwingUtilities.invokeLater(() ->
                lineList.get(line).setText(text)
        );
    }

    public JButton getBtn01() {
        return btn01;
    }

    public JButton getBtn02() {
        return btn02;
    }

    public void addActionListenerToBTN01(ActionListener actionListener) {
        btn01.addActionListener(actionListener);
    }

    public void addActionListenerToBTN02(ActionListener actionListener) {
        btn02.addActionListener(actionListener);
    }


    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        dialogPane = new JPanel();
        tabPanel = new JTabbedPane();
        content1 = new JPanel();
        pnlLCD = new JPanel();
        content2 = new JPanel();
        scrlMatrix = new JScrollPane();
        buttonBar = new JPanel();
        btn01 = new JButton();
        btn02 = new JButton();

        //======== this ========
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(235, 323));
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());

            //======== tabPanel ========
            {

                //======== content1 ========
                {
                    content1.setLayout(new BorderLayout(2, 2));

                    //======== pnlLCD ========
                    {
                        pnlLCD.setBackground(Color.gray);
                        pnlLCD.setForeground(Color.black);
                        pnlLCD.setLayout(new BoxLayout(pnlLCD, BoxLayout.PAGE_AXIS));
                    }
                    content1.add(pnlLCD, BorderLayout.SOUTH);
                }
                tabPanel.addTab("Main", content1);

                //======== content2 ========
                {
                    content2.setLayout(new BoxLayout(content2, BoxLayout.PAGE_AXIS));
                    content2.add(scrlMatrix);
                }
                tabPanel.addTab("Matrix", content2);
                tabPanel.setEnabledAt(1, false);
            }
            dialogPane.add(tabPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridLayout());

                //---- btn01 ----
                btn01.setText("BTN01");
                btn01.setAlignmentY(0.0F);
                btn01.setFont(new Font(".SF NS Text", Font.BOLD, 20));
                buttonBar.add(btn01);

                //---- btn02 ----
                btn02.setText("BTN02");
                btn02.setAlignmentY(0.0F);
                btn02.setFont(new Font(".SF NS Text", Font.BOLD, 20));
                btn02.setEnabled(false);
                btn02.setToolTipText("reserved for later use");
                buttonBar.add(btn02);
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        setSize(237, 325);
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JPanel dialogPane;
    private JTabbedPane tabPanel;
    private JPanel content1;
    private JPanel pnlLCD;
    private JPanel content2;
    private JScrollPane scrlMatrix;
    private JPanel buttonBar;
    private JButton btn01;
    private JButton btn02;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}

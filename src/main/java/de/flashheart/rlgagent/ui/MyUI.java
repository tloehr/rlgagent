/*
 * Created by JFormDesigner on Mon Aug 23 15:29:20 CEST 2021
 */

package de.flashheart.rlgagent.ui;

import javax.swing.event.*;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import de.flashheart.rlgagent.misc.Configs;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


/**
 * @author Torsten Löhr
 */
@Log4j2
public class MyUI extends JFrame {
    private final Configs configs;
    HashMap<String, MyLED> pinMap;
    ArrayList<JLabel> lineList;
    private JPanel leds, sirens;
    private boolean first_run = true;
    private final Font large, normal;

    public MyUI(Configs configs) {
        this.configs = configs;
        // made this especially for the simulation environment.
        // restores the window to the last used position.
        // great for screen setups with MANY agent JFrames
        int locationX = configs.getInt(Configs.FRAME_LOCATION_X);
        int locationY = configs.getInt(Configs.FRAME_LOCATION_Y);
        large = new Font(".SF NS Text", Font.BOLD, 20);
        normal = new Font(".SF NS Text", Font.BOLD, 14);

        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
            FlatDarkLaf.setup();
        } catch (Exception ex) {
            log.fatal("Failed to initialize LaF");
            System.exit(0);
        }
        initComponents();
        setTitle(configs.getAgentname());
        initLEDs();
        initLCD();

        setLocation(locationX, locationY);


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

        leds = new JPanel();
        leds.setLayout(new BoxLayout(leds, BoxLayout.PAGE_AXIS));
        leds.setAlignmentX(Component.LEFT_ALIGNMENT);
        sirens = new JPanel();
        sirens.setLayout(new BoxLayout(sirens, BoxLayout.PAGE_AXIS));

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

        tabPanel.setSelectedIndex(configs.getInt(Configs.SELECTED_TAB));
        set_page_content_for_tab(configs.getInt(Configs.SELECTED_TAB));

//        content1.add(page, BorderLayout.CENTER);
        //content2.add(page2, BorderLayout.CENTER);
    }

    JPanel get_flag_page() {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.LINE_AXIS));
        set_icon_size(Configs.ALL_LEDS, 48);
        page.add(leds);
        return page;
    }

    JPanel get_siren_page() {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.LINE_AXIS));
        set_icon_size(Configs.ALL_SIRENS, 48);
        set_font(Configs.ALL_SIRENS, large);
        page.add(sirens);
        return page;
    }

    JPanel get_full_page() {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.LINE_AXIS));
        set_icon_size(Configs.ALL_PINS, 22);
        set_font(Configs.ALL_SIRENS, normal);
        page.add(leds);
        page.add(Box.createRigidArea(new Dimension(20, 0)));
        page.add(sirens);
        return page;
    }

    void set_icon_size(String[] list, int size) {
        Arrays.stream(list).forEach(s -> pinMap.get(s).setIconSize(size));
    }

    void set_font(String[] list, Font font) {
        Arrays.stream(list).forEach(s -> pinMap.get(s).setFont(font));
    }

    private void tabPanelStateChanged(ChangeEvent e) {
        if (leds == null) return;
        configs.setInt(Configs.SELECTED_TAB, tabPanel.getSelectedIndex());
        set_page_content_for_tab(tabPanel.getSelectedIndex());

    }

    void set_page_content_for_tab(int tab) {
        SwingUtilities.invokeLater(() -> {
            content1.removeAll();
            content2.removeAll();
            content3.removeAll();
            JLabel lbl = new JLabel(configs.getAgentname());
            lbl.setFont(large);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (tab == 0) { // main
                content1.add(get_full_page(), BorderLayout.CENTER);
                content1.add(lbl, BorderLayout.EAST);
                content1.add(pnlLCD, BorderLayout.SOUTH);
                int width = configs.getInt(Configs.FRAME_WIDTH0);
                int height = configs.getInt(Configs.FRAME_HEIGHT0);
                if (width >= 0) setSize(new Dimension(width, height));
            } else if (tab == 1) { // flag
                content2.add(lbl);
                content2.add(get_flag_page());
                int width = configs.getInt(Configs.FRAME_WIDTH1);
                int height = configs.getInt(Configs.FRAME_HEIGHT1);
                if (width >= 0) setSize(new Dimension(width, height));
            } else { // sirens
                content3.add(lbl);
                content3.add(get_siren_page());
                int width = configs.getInt(Configs.FRAME_WIDTH2);
                int height = configs.getInt(Configs.FRAME_HEIGHT2);
                if (width >= 0) setSize(new Dimension(width, height));
            }
            revalidate();
            repaint();
        });
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

    private void thisComponentResized(ComponentEvent e) {
        if (tabPanel.getSelectedIndex() == 0) {
            configs.setInt(Configs.FRAME_WIDTH0, e.getComponent().getWidth());
            configs.setInt(Configs.FRAME_HEIGHT0, e.getComponent().getHeight());
        } else if (tabPanel.getSelectedIndex() == 1) {
            configs.setInt(Configs.FRAME_WIDTH1, e.getComponent().getWidth());
            configs.setInt(Configs.FRAME_HEIGHT1, e.getComponent().getHeight());
        } else {
            configs.setInt(Configs.FRAME_WIDTH2, e.getComponent().getWidth());
            configs.setInt(Configs.FRAME_HEIGHT2, e.getComponent().getHeight());
        }
    }

    private void thisComponentMoved(ComponentEvent e) {
        configs.setInt(Configs.FRAME_LOCATION_X, e.getComponent().getX());
        configs.setInt(Configs.FRAME_LOCATION_Y, e.getComponent().getY());
    }

//    public JButton getBtn02() {
//        return btn02;
//    }
//
//    public void addActionListenerToBTN01(ActionListener actionListener) {
//        btn01.addActionListener(actionListener);
//    }

//    public void addActionListenerToBTN02(ActionListener actionListener) {
//        btn02.addActionListener(actionListener);
//    }

//    /**
//     * the position of the frame was changed. storing new position in configs.
//     * @param e
//     */
//    private void thisComponentMoved(ComponentEvent e) {
//
//
//    }


    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        dialogPane = new JPanel();
        tabPanel = new JTabbedPane();
        content1 = new JPanel();
        content2 = new JPanel();
        content3 = new JPanel();
        buttonBar = new JPanel();
        btn01 = new JButton();
        pnlLCD = new JPanel();

        //======== this ========
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(null);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                thisComponentMoved(e);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                thisComponentResized(e);
            }
        });
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());

            //======== tabPanel ========
            {
                tabPanel.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
                tabPanel.addChangeListener(e -> tabPanelStateChanged(e));

                //======== content1 ========
                {
                    content1.setLayout(new BorderLayout(2, 2));
                }
                tabPanel.addTab("Full", content1);

                //======== content2 ========
                {
                    content2.setLayout(new BoxLayout(content2, BoxLayout.PAGE_AXIS));
                }
                tabPanel.addTab("Flag", content2);

                //======== content3 ========
                {
                    content3.setLayout(new BoxLayout(content3, BoxLayout.PAGE_AXIS));
                }
                tabPanel.addTab("Sirens", content3);
            }
            dialogPane.add(tabPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridLayout());

                //---- btn01 ----
                btn01.setText(null);
                btn01.setAlignmentY(0.0F);
                btn01.setFont(new Font(".SF NS Text", Font.BOLD, 20));
                btn01.setIcon(new ImageIcon(getClass().getResource("/artwork/48x48/button.png")));
                buttonBar.add(btn01);
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        setLocationRelativeTo(getOwner());

        //======== pnlLCD ========
        {
            pnlLCD.setBackground(Color.gray);
            pnlLCD.setForeground(Color.black);
            pnlLCD.setLayout(new BoxLayout(pnlLCD, BoxLayout.PAGE_AXIS));
        }
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JPanel dialogPane;
    private JTabbedPane tabPanel;
    private JPanel content1;
    private JPanel content2;
    private JPanel content3;
    private JPanel buttonBar;
    private JButton btn01;
    private JPanel pnlLCD;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}

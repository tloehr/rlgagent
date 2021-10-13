/*
 * Created by JFormDesigner on Mon Aug 23 15:29:20 CEST 2021
 */

package de.flashheart.rlgagent.ui;

import de.flashheart.rlgagent.misc.Configs;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * @author Torsten Löhr
 */
@Log4j2
public class MyUI extends JFrame {
    private final int MAX_LOG_LINES = 200;
    HashMap<String, MyLED> pinMap;
    ArrayList<JLabel> lineList;

    public MyUI(String title) {
        initComponents();
        setTitle(title);
        initLEDs();
        initLCD();
        pack();
    }

    private void initLCD() {
        lineList = new ArrayList<>();
    }

//    private void initLogger() {
//        // keeps the log window under MAX_LOG_LINES lines to prevent out of memory exception
//        txtLogger.getDocument().addDocumentListener(new DocumentListener() {
//            @Override
//            public void insertUpdate(DocumentEvent e) {
//                SwingUtilities.invokeLater(() -> {
//                    Element root = e.getDocument().getDefaultRootElement();
//                    while (root.getElementCount() > MAX_LOG_LINES) {
//                        Element firstLine = root.getElement(0);
//                        try {
//                            e.getDocument().remove(0, firstLine.getEndOffset());
//                        } catch (BadLocationException ble) {
//                            log.error(ble);
//                        }
//                    }
//                });
//            }
//
//            @Override
//            public void removeUpdate(DocumentEvent e) {
//            }
//
//            @Override
//            public void changedUpdate(DocumentEvent e) {
//            }
//        });
//    }
//
//    public void addLog(String text) {
//        SwingUtilities.invokeLater(() -> {
//            txtLogger.append(text + "\n");
//            scrollPane1.getVerticalScrollBar().setValue(scrollPane1.getVerticalScrollBar().getMaximum());
//        });
//    }

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
        pinMap.put(Configs.OUT_BUZZER, new MyLED(Configs.OUT_BUZZER, Color.DARK_GRAY));
        JPanel pinPanel= new JPanel();
        //pinPanel.setLayout(new BoxLayout(pinPanel, BoxLayout.PAGE_AXIS));
        pinPanel.setLayout(new GridLayout(5, 2, 5, 5));
        pinPanel.add(pinMap.get(Configs.OUT_LED_WHITE));
        pinPanel.add(pinMap.get(Configs.OUT_SIREN1));
        pinPanel.add(pinMap.get(Configs.OUT_LED_RED));
        pinPanel.add(pinMap.get(Configs.OUT_SIREN2));
        pinPanel.add(pinMap.get(Configs.OUT_LED_YELLOW));
        pinPanel.add(pinMap.get(Configs.OUT_SIREN3));
        pinPanel.add(pinMap.get(Configs.OUT_LED_GREEN));
        pinPanel.add(pinMap.get(Configs.OUT_BUZZER));
        pinPanel.add(pinMap.get(Configs.OUT_LED_BLUE));
        content1.add(pinPanel, BorderLayout.CENTER);
    }

    public void setState(String name, boolean on) {
        pinMap.get(name).setState(on);
    }

    public void addLCDLine(int cols) {
        JLabel lbl = new JLabel(StringUtils.repeat(" ", cols));
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 16));
        lineList.add(lbl);
        pnlLCD.add(lbl);
    }

    public void setLine(int line, String text) {
        SwingUtilities.invokeLater(() ->
                lineList.get(line).setText(text)
        );
    }

    public void addActionListenerToBTN01(ActionListener actionListener) {
        btn01.addActionListener(actionListener);
    }

    public void addActionListenerToBTN02(ActionListener actionListener) {
        btn02.addActionListener(actionListener);
    }


    private void theButtonActionPerformed(ActionEvent e) {
        // TODO add your code here
    }


    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        dialogPane = new JPanel();
        tabPanel = new JTabbedPane();
        content1 = new JPanel();
        content2 = new JPanel();
        scrlMatrix = new JScrollPane();
        pnlLCD = new JPanel();
        buttonBar = new JPanel();
        btn01 = new JButton();
        btn02 = new JButton();

        //======== this ========
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
                }
                tabPanel.addTab("Main", content1);

                //======== content2 ========
                {
                    content2.setLayout(new BoxLayout(content2, BoxLayout.PAGE_AXIS));
                    content2.add(scrlMatrix);

                    //======== pnlLCD ========
                    {
                        pnlLCD.setBackground(Color.cyan);
                        pnlLCD.setLayout(new BoxLayout(pnlLCD, BoxLayout.PAGE_AXIS));
                    }
                    content2.add(pnlLCD);
                }
                tabPanel.addTab("Displays", content2);
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
                btn01.addActionListener(e -> theButtonActionPerformed(e));
                buttonBar.add(btn01);

                //---- btn02 ----
                btn02.setText("BTN02");
                btn02.setAlignmentY(0.0F);
                btn02.setFont(new Font(".SF NS Text", Font.BOLD, 20));
                btn02.addActionListener(e -> theButtonActionPerformed(e));
                buttonBar.add(btn02);
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JPanel dialogPane;
    private JTabbedPane tabPanel;
    private JPanel content1;
    private JPanel content2;
    private JScrollPane scrlMatrix;
    private JPanel pnlLCD;
    private JPanel buttonBar;
    private JButton btn01;
    private JButton btn02;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}

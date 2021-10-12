package de.flashheart.rlgagent.hardware.abstraction;

import de.flashheart.rlgagent.hardware.I2CLCD;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.Tools;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.extern.log4j.Log4j2;
import org.codehaus.plexus.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
public class MyLCD implements Runnable {
    public static final char LCD_DEGREE_SYMBOL = 223;
    public static final char LCD_UMLAUT_A = 0xe4;
    private final int WIFI_PERFECT = -30;
    private final int WIFI_EXCELLENT = -50;
    private final int WIFI_GOOD = -60;
    private final int WIFI_FAIR = -67;
    private final int WIFI_MINIMUM = -70;
    private final int WIFI_UNSTABLE = -80;
    private final int WIFI_BAD = -90;

    private final int CYCLES_PER_PAGE = 4;
    private static final long MILLIS_PER_CYCLE = 500l;
    private final int cols, rows;
    private final Thread thread;


    private static final long SLEEP_PER_CYCLE = 1000l;
    private long remaining, time_difference_since_last_cycle;
    private long last_cycle_started_at;


    private final ArrayList<LCDPage> pages;
    private int active_page;
    private long loopcounter = 0;
    private int prev_page = 0;
    private ReentrantLock lock;
    private Optional<I2CLCD> i2CLCD;
    private final Optional<MyUI> myUI;
    private final Configs configs;
    private int wifiQuality = 0; // 0 bis 6. 6 ist die beste

    public MyLCD( Optional<MyUI> myUI, Configs configs, Optional<I2CLCD> i2CLCD) {
        this.cols = Integer.parseInt(configs.get(Configs.LCD_COLS));
        this.rows =  Integer.parseInt(configs.get(Configs.LCD_ROWS));
        this.myUI = myUI;
        this.configs = configs;
        this.i2CLCD = i2CLCD;

        // create lines on the gui
        myUI.ifPresent(myUI1 -> {
            for (int l = 0; l < rows; l++) {
                myUI1.addLCDLine(cols);
            }
        });

        lock = new ReentrantLock();
        thread = new Thread(this);
        pages = new ArrayList<>();
        pages.add(new LCDPage()); // there is always one page.
        thread.start();
    }

    public int getRows() {
        return rows;
    }

    public void reset() {
        lock.lock();
        try {
            pages.clear();
            pages.add(new LCDPage()); // there is always one page.
            active_page = 0;
            clear(active_page);
        } finally {
            lock.unlock();
        }
    }

    public void selectPage(int active_page) {
        if (active_page < 1 || active_page > pages.size()) return;
        this.active_page = active_page;
        page_to_display();
    }

    /**
     * Fügt eine neue Seite hinzu
     *
     * @return nummer der neuen Seite
     */
    public int addPage() {
        lock.lock();
        try {
            pages.add(new LCDPage());
        } finally {
            lock.unlock();
        }
        return pages.size() - 1;
    }

    /**
     * löscht die aktuelle Seite. Eine Seite bleibt immer stehen.
     */
    public void deletePage(int page) {
        if (pages.size() == 1) return;

        lock.lock();
        try {
            pages.remove(page - 1);
            active_page = 1;
            page_to_display();
        } finally {
            lock.unlock();
        }

    }

    private void inc_page() {
        prev_page = active_page;
        active_page++;
        if (active_page > pages.size() - 1) active_page = 0;
    }

    private void page_to_display() {
        LCDPage currentPage = pages.get(active_page);
        // Schreibt alle Zeilen der aktiven Seite.

        for (int r = 0; r < rows; r++) {
            String line = currentPage.getLine(r).isEmpty() ? StringUtils.repeat(" ", 16) : StringUtils.rightPad(currentPage.getLine(r), 16);
            log.trace("VISIBLE PAGE #" + active_page + " Line" + r + ": " + line);
            if (i2CLCD.isPresent()) {
                i2CLCD.get().display_string(line, r);
            }
            if (myUI.isPresent()) {
                myUI.get().setLine(r, line);
            }

        }


    }

    private void clearPage() {
        pages.get(active_page).clear();

    }

    /**
     * @param page 1..pages.size()
     * @param line 1..rows
     * @param text
     */
    public void setLine(int page, int line, String text) {
        if (page < 0 || page > pages.size() - 1) return;
        if (line < 1 || line > rows) return;
        pages.get(page).lines.set(line - 1, StringUtils.rightPad(StringUtils.left(text, cols), cols - 1));
    }

    public void setCenteredLine(int page, int line, String text) {
        setLine(page, line, StringUtils.center(StringUtils.left(text, cols), cols));
    }

    public void clear(int page) {
        if (page < 0 || page > pages.size() - 1) return;
        for (int l = 1; l <= rows; l++) {
            setLine(page, l, "");
        }
    }

    private void updateTimer() {
        // Entweder wir brauchen keinen Timer oder er ist abgelaufen
        // Aber kümmert uns hier nicht, muss der Commander machen
        if (remaining < 0) return;
        long now = System.currentTimeMillis();
        time_difference_since_last_cycle = now - last_cycle_started_at;
        last_cycle_started_at = now;
        remaining = remaining - time_difference_since_last_cycle;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    @Override
    public void run() {
        while (!thread.isInterrupted()) {
            try {
                lock.lock();
                try {
                    if (loopcounter % CYCLES_PER_PAGE == 0) {
                        inc_page();
                    }

                    if (active_page == 0) updatePage0();

                    if (pages.size() == 1 || active_page != prev_page) {
                        page_to_display();
                        prev_page = active_page;
                    }
                } catch (Exception ex) {
                    log.error(ex);
                } finally {
                    lock.unlock();
                }
                Thread.sleep(MILLIS_PER_CYCLE);
                loopcounter++;
            } catch (InterruptedException ie) {
                log.error(ie);
            }
        }
    }

    private void updatePage0() {
        updateTimer();
        //if (loopcounter % 10 == 0) updateWifiSignalStrength();
        String time = "";
        if (remaining > 0) {
            LocalDateTime remainingTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(remaining),
                    TimeZone.getTimeZone("UTC").toZoneId());

            time = "Time: " + remainingTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM));
        }
        setLine(0, 1, configs.get(Configs.MY_ID) + " wifi:" + Tools.WIFI[wifiQuality]);
        setLine(0, 2, time);
    }

    public void setWifiQuality(int wifiQuality) {
        this.wifiQuality = wifiQuality;
    }

    /**
     * Das ist eine Seite, so wie sie auf dem Display angezeigt wird. Man kann jede Seite getrennt setzen. Gibts es mehr
     * als eine Seite, "cyclen" die im Sekundenabstand durch Wird eine Seite verändert, wird sie sofort angezeigt und es
     * cycled dann von da ab weiter.
     */
    private class LCDPage {
        // Start stepping through the array from the beginning
        private ArrayList<String> lines;

        public LCDPage() {
            this.lines = new ArrayList<>(rows);

            clear();
        }

        public void setLine(int num, String text) {
            lines.set(num, text);
        }

        public String getLine(int num) {
            return lines.get(num);
        }

        public void clear() {
            lines.clear();
            for (int r = 0; r < rows; r++) {
                lines.add("");
                setLine(r, "");
            }
        }

    }

}


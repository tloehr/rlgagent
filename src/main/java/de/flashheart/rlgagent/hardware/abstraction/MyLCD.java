package de.flashheart.rlgagent.hardware.abstraction;

import de.flashheart.rlgagent.hardware.I2CLCD;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
public class MyLCD implements Runnable {
    public static final char LCD_DEGREE_SYMBOL = 223;
    public static final char LCD_UMLAUT_A = 0xe4;

    private final int CYCLES_PER_PAGE = 4;
    private static final long MILLIS_PER_CYCLE = 500l;
    private final int cols, rows;
    private final Thread thread;


    private static final long SLEEP_PER_CYCLE = 1000l;
    private long remaining, time_difference_since_last_cycle;
    private long last_cycle_started_at;
    private Optional<String> score;

    private boolean network_lost; // show emergency data to help debug network trouble

    private final ArrayList<LCDPage> pages;
    private final HashMap<String, Integer> page_map;
    private int active_page;
    private long loopcounter = 0;
    private int prev_page = 0;
    private ReentrantLock lock;
    private Optional<I2CLCD> i2CLCD;
    private final Optional<MyUI> myUI;
    private final Configs configs;
    private String wifi_response_by_driver;

    public MyLCD(Optional<MyUI> myUI, Configs configs, Optional<I2CLCD> i2CLCD) {
        this.cols = Integer.parseInt(configs.get(Configs.LCD_COLS));
        this.rows = Integer.parseInt(configs.get(Configs.LCD_ROWS));
        this.myUI = myUI;
        this.configs = configs;
        this.i2CLCD = i2CLCD;
        this.score = Optional.empty();
        this.wifi_response_by_driver = "?";
        network_lost = true;

        // create lines on the gui
        myUI.ifPresent(myUI1 -> {
            for (int l = 0; l < rows; l++) {
                myUI1.addLCDLine(cols);
            }
        });

        lock = new ReentrantLock();
        thread = new Thread(this);
        pages = new ArrayList<>();
        page_map = new HashMap<>();
        init();
        thread.start();
    }

    public int getRows() {
        return rows;
    }

    public void init() {
        lock.lock();
        try {
            pages.clear();
            page_map.clear();
            pages.add(new LCDPage()); // there is always one page.
            page_map.put("page0", 0);
            active_page = 0;
        } finally {
            lock.unlock();
        }
    }

    public void setScore(Optional<String> score) {
        this.score = score;
    }

    public void selectPage(String handle) {
        if (!page_map.containsKey(handle)) return;

        this.active_page = page_map.get(handle);
        display_active_page();
    }

    /**
     * Adds a new page
     *
     * @param handle to access the page later
     */
    public void addPage(String handle) {
        lock.lock();
        try {
            pages.add(new LCDPage());
            page_map.put(handle, pages.size() - 1);
        } finally {
            lock.unlock();
        }
    }

    public void deletePage(String handle) {
        if (handle.equalsIgnoreCase("page0")) return;
        if (!page_map.containsKey(handle)) return;

        lock.lock();
        try {
            pages.remove(page_map.get(handle));
            page_map.remove(handle);
            active_page = page_map.get("page0");
            display_active_page();
        } finally {
            lock.unlock();
        }

    }

    private void inc_page() {
        prev_page = active_page;
        active_page++;
        if (active_page > pages.size() - 1) active_page = 0;
    }

    private void display_active_page() {
        LCDPage currentPage = pages.get(active_page);
        // Schreibt alle Zeilen der aktiven Seite.

        for (int r = 0; r < rows; r++) {
            String line = currentPage.getLine(r).isEmpty() ? StringUtils.repeat(" ", cols) : StringUtils.rightPad(currentPage.getLine(r), cols);
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
     * @param handle for the wanted page
     * @param line   1..rows
     * @param text
     */
    public void setLine(String handle, int line, String text) {
        if (!page_map.containsKey(handle)) return;
        if (line < 1 || line > rows) return;

        pages.get(page_map.get(handle)).lines.set(line - 1, StringUtils.rightPad(StringUtils.left(text, cols), cols - 1));
    }

    public void setCenteredLine(String handle, int line, String text) {
        setLine(handle, line, StringUtils.center(StringUtils.left(text, cols), cols));
    }

    public void clear(String handle) {
        for (int l = 1; l <= rows; l++) {
            setLine(handle, l, "");
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

    public void setNetwork_lost(boolean network_lost) {
        this.network_lost = network_lost;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining * 1000l;
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

                    if (active_page == 0) {
                        if (network_lost) network_debug_page();
                        else updatePage0();
                    }

                    if (pages.size() == 1 || active_page != prev_page) {
                        display_active_page();
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

    private void network_debug_page() {
        setCenteredLine("page0", 1, configs.get(Configs.MY_ID) + " wifi:" + wifi_response_by_driver);
        setCenteredLine("page0", 2, "");
        setCenteredLine("page0", 3, "");
        setCenteredLine("page0", 4, "network debug mode");
    }

    private void updatePage0() {
        updateTimer();

        if (remaining > 0) {
            LocalDateTime remainingTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(remaining),
                    TimeZone.getTimeZone("UTC").toZoneId());

            String time = "Time: " + remainingTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM));
            setCenteredLine("page0", 1, time);
        } else {
            setCenteredLine("page0", 1, "");
        }
        setCenteredLine("page0", 2, score.orElse(""));
        // line 3 and 4 can be used by the user
    }

//    public void setWifiQuality(int wifiQuality) {
//        this.wifiQuality = wifiQuality;
//    }

    public void setWifi(String wifi_response_by_driver) {
        this.wifi_response_by_driver = wifi_response_by_driver;
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


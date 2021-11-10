package de.flashheart.rlgagent.hardware.abstraction;

import de.flashheart.rlgagent.hardware.I2CLCD;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.Tools;
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
/**
 * This class is handling the hardware of a Hitachie I2C LCD display and (at the same time) simulates the output on a desktop
 * screen, if available. It organizes the display in pages which can be added during runtime by naming it with a
 * string handle. There is always a page called "page0". Pages cycle through with a delaytime of cycles_per_page *
 * MILLIS_PER_CYCLE (4*500ms by default) The CYCLES_PER_PAGE can be changed during runtime.
 */
public class MyLCD implements Runnable {
    public static final char LCD_DEGREE_SYMBOL = 223;
    public static final char LCD_UMLAUT_A = 0xe4;

    private int cycles_per_page = 4;
    private final long MILLIS_PER_CYCLE = 500l;

    private final int cols, rows;
    private final Thread thread;

    // timers section
    // it may seem odd, but for the agent all timers are just
    // something that will show up on the display
    // so we maintain them here
    private long time_difference_since_last_cycle;
    private HashMap<String, Long> timers;
    private HashMap<String, String> variables; // replacement variables for text lines containing something like ${template}

    private long last_cycle_started_at;


    private boolean network_lost; // show emergency data to help debug network trouble

    private final ArrayList<LCDPage> pages;
    private final HashMap<String, Integer> page_map; // assigns key to pagenumber
    private int active_page;
    private long loopcounter = 0;
    private int prev_page = 0;
    private ReentrantLock lock;
    private Optional<I2CLCD> i2CLCD;
    private final Optional<MyUI> myUI;
    private final Configs configs;
    //private String wifi_response_by_driver;


    /**
     * @param myUI    if we are running on a desktop, we will receive a handle to the gui. this gui is used to simulate
     *                the display behaviour on the screen
     * @param configs the configs object for reading settings
     * @param i2CLCD  if there is a HD44780 based i2c lcd display, we will have a reference here
     */
    public MyLCD(Optional<MyUI> myUI, Configs configs, Optional<I2CLCD> i2CLCD) {
        this.cols = Integer.parseInt(configs.get(Configs.LCD_COLS));
        this.rows = Integer.parseInt(configs.get(Configs.LCD_ROWS));
        this.myUI = myUI;
        this.configs = configs;
        this.i2CLCD = i2CLCD;
        network_lost = true;
        timers = new HashMap<>();
        variables = new HashMap<>();

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

    /**
     * removes all pages but one "page0"
     */
    public void init() {
        lock.lock();
        try {
            pages.clear();
            page_map.clear();
            pages.add(new LCDPage()); // there is always one page.
            page_map.put("page0", 0);
            active_page = 0;
            timers.clear();
            variables.clear();
            // set defaults for variables
            setVariable("wifi","");
            setVariable("agversion",configs.getBuildProperties("my.version"));
            setVariable("agbuild",configs.getBuildProperties("buildNumber"));
            setVariable("agbdate",configs.getBuildProperties("buildDate"));
        } finally {
            lock.unlock();
        }
    }

    /**
     * sets the visible page
     *
     * @param handle the page that should be visible
     */
    public void selectPage(String handle) {
        if (!page_map.containsKey(handle)) return;

        this.active_page = page_map.get(handle);
        display_active_page();
    }

    /**
     * Adds a new page
     *
     * @param handle to access the page
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

    /**
     * deletes a page and starts the page cycle from the beginning
     *
     * @param handle of page to be deleted
     */
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
        if (pages.size() == 1) return;
        prev_page = active_page;
        active_page++;
        if (active_page > pages.size() - 1) active_page = 0;
    }

    /**
     * All content changes on lines and pages are done on virtual pages in the background. This method reads the
     * currently active background page and writes it to the visible display (onto the destop screen AND the LCD)
     */
    private void display_active_page() {
        LCDPage currentPage = pages.get(active_page);
        // Schreibt alle Zeilen der aktiven Seite.

        for (int r = 0; r < rows; r++) {
            String line = currentPage.getLine(r).isEmpty() ? StringUtils.repeat(" ", cols) : StringUtils.rightPad(StringUtils.left(currentPage.getLine(r), cols), cols - 1);
            log.trace("VISIBLE PAGE #" + active_page + " Line" + r + ": " + line);
//            if (log.getLevel().equals(Level.DEBUG) && r == rows - 1) { // last line, we want a pagenumber here, when in debug mode
//                String pagenumber = Integer.toString(active_page);
//                line = StringUtils.overlay(line, pagenumber, line.length()-pagenumber.length(), line.length());
//            }
            if (i2CLCD.isPresent()) {
                i2CLCD.get().display_string(line, r + 1);
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

        pages.get(page_map.get(handle)).lines.set(line - 1, text);
    }

    /**
     * the calculation of the timer is one of the few things the agent does on its own. It simply counts down a given
     * remaining timer (which has been broadcasted by the commander). When it runs out, the timer simply disappears from
     * the display. That's it.
     */
    private void calculate_timers() {
        long now = System.currentTimeMillis();
        time_difference_since_last_cycle = now - last_cycle_started_at;
        last_cycle_started_at = now;

        // recalculate all timers
        timers.keySet().forEach(key -> {
            long time = timers.get(key);
            time = time - time_difference_since_last_cycle;
            LocalDateTime ldtTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), TimeZone.getTimeZone("UTC").toZoneId());
            timers.put(key, time);
            // timer for string replacement
            if (time > 0) variables.put(key, ldtTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)));
            else variables.remove(key);
        });
        // remove obsolete timers
        timers.entrySet().removeIf(stringLongEntry -> stringLongEntry.getValue() <= 0);
    }

    public void setTimer(String key, long time) {
        timers.put("${" + key + "}", (time + 1) * 1000l);
    }

    public void setVariable(String key, String var) {
        variables.put("${" + key + "}", var);
    }

    public void setNetwork_lost(boolean network_lost) {
        this.network_lost = network_lost;
    }

    @Override
    public void run() {
        while (!thread.isInterrupted()) {
            try {
                lock.lock();
                try {
                    if (loopcounter % cycles_per_page == 0) {
                        inc_page();
                    }

                    if (pages.size() == 1 || active_page != prev_page) {
                        calculate_timers();
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

    /**
     * sets the page cycle multiplicator. changes will be used immediately
     *
     * @param cycles_per_page
     */
    public void setCycles_per_page(int cycles_per_page) {
        this.cycles_per_page = cycles_per_page;
    }

    /**
     * internal helper class to store the contents of a page
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
            //final StringBuffer line = new StringBuffer();
            // replace timers with their variables, if present
            return Tools.replaceVariables(lines.get(num), variables);
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


package de.flashheart.rlgagent.hardware.abstraction;

import de.flashheart.rlgagent.hardware.I2CLCD;
import de.flashheart.rlgagent.misc.Configs;
import de.flashheart.rlgagent.misc.Tools;
import de.flashheart.rlgagent.ui.MyUI;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
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
    private boolean dirty; // page refresh needed
    private ArrayList<LCDPage> pages;

    private int visible_page_index = 0;
    private long loopcounter = 0;
    private ReentrantLock lock;
    private Optional<I2CLCD> i2CLCD;
    private final Optional<MyUI> myUI;
    private final Configs configs;


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
        init();
        thread.start();
    }

    public void welcome_page(){
        setLine("page0", 1, "  Welcome to the  ");
        setLine("page0", 2, "RLGAgent ${agversion}.${agbuild}");
        setLine("page0", 3, "");
        setLine("page0", 4, "Initializing...");
    }

    /**
     * removes all pages but one "page0"
     */
    public void init() {
        lock.lock();
        try {
            dirty = false;
            pages.clear();
            pages.add(new LCDPage("page0"));
            visible_page_index = 0;
            timers.clear();
            variables.clear();
            // set defaults for variables
            setVariable("wifi", "--");
            setVariable("ssid", "--");
            setVariable("agversion", configs.getBuildProperties("my.version"));
            setVariable("agbuild", configs.getBuildProperties("buildNumber"));
            setVariable("agbdate", configs.getBuildProperties("buildDate"));

            welcome_page();

        } finally {
            lock.unlock();
        }
    }

    /**
     * sets the visible page
     *
     * @param handle the page that should be visible
     */
    Optional<LCDPage> getPage(String handle) {
        return pages.stream().filter(lcdPage -> lcdPage.getName().equalsIgnoreCase(handle)).findFirst();
    }

    /**
     * Adds a new page
     *
     * @param handle to access the page
     */
    public void addPage(String handle) {
        if (handle.equalsIgnoreCase("page0")) return;
        if (pageExists(handle)) return;
        lock.lock();
        log.trace("adding page {}", handle);
        try {
            pages.add(new LCDPage(handle));
        } finally {
            lock.unlock();
        }
    }


    /**
     * deletes a page and starts the page cycle from the beginning
     *
     * @param handle of page to be deleted
     */
    public void delPage(String handle) {
        if (handle.equalsIgnoreCase("page0")) return;
        if (!pageExists(handle)) return;
        log.trace("deleting page {}", handle);
        lock.lock();
        try {
            try {
                pages.removeIf(lcdPage -> lcdPage.getName().equalsIgnoreCase(handle));
                visible_page_index = 0;
                display_active_page();
            } catch (Exception e) {
                log.error(e);
            }
        } finally {
            lock.unlock();
        }
    }

    private void next_page() {
        log.trace("pages size {}", pages.size());
        if (pages.size() == 1) return;

        lock.lock();
        try {
            visible_page_index++;
            if (visible_page_index >= pages.size()) visible_page_index = 0;
            log.trace("index of active_page {}", visible_page_index);
            //active_page = pages.get(visible_page_index);
        } catch (Exception e) {
            log.error(e);
        } finally {
            lock.unlock();
        }

    }

    /**
     * All content changes on lines and pages are done on virtual pages in the background. This method reads the
     * currently active background page and writes it to the visible display (onto the destop screen AND the LCD)
     */
    private void display_active_page() {
        for (int r = 0; r < rows; r++) {
            String line = pages.get(visible_page_index).getLine(r).isEmpty() ? StringUtils.repeat(" ", cols) : StringUtils.rightPad(StringUtils.left(pages.get(visible_page_index).getLine(r), cols), cols); // -1 ??
            log.trace("VISIBLE PAGE #" + visible_page_index + " Line" + r + ": " + line);
//            if (log.getLevel().equals(Level.trace) && r == rows - 1) { // last line, we want a pagenumber here, when in trace mode
//                String pagenumber = Integer.toString(visible_page_index);
//                line = StringUtils.overlay(line, pagenumber, line.length() - pagenumber.length(), line.length());
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
        pages.get(visible_page_index).clear();
    }

    public boolean pageExists(String handle) {
        return pages.stream().filter(lcdPage -> lcdPage.getName().equalsIgnoreCase(handle)).count() > 0;
    }

    /**
     * @param handle for the wanted page. Will be created if necessary.
     * @param line   1..rows
     * @param text
     */
    public void setLine(String handle, int line, String text) {
        if (!pageExists(handle)) addPage(handle);
        if (line < 1 || line > rows) return;
        getPage(handle).ifPresent(lcdPage -> lcdPage.setLine(line - 1, text));
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
            if (time > 0) {
                time = time - time_difference_since_last_cycle;
                timers.put(key, time);
                if (time > 0)
                    setVariable(key, LocalTime.ofSecondOfDay(time / 1000l).format(DateTimeFormatter.ofPattern("mm:ss")));
                else setVariable(key, "");
            }
        });
    }

    public void setTimer(String key, long time) {
        log.trace("setting timer {} to {}", key, time);
        timers.put(key, time * 1000l);
    }

    public void setVariable(String key, String var) {
        log.trace("setting var {} to {}", key, var);
        variables.put("${" + key + "}", var);
    }

    @Override
    public void run() {
        while (!thread.isInterrupted()) {
            try {
                lock.lock();
                try {

                    if (pages.size() > 1 && loopcounter % cycles_per_page == 0) {
                        next_page();
                        calculate_timers();
                        display_active_page();
                    }

                    if (pages.size() == 1) {
                        calculate_timers();
                        display_active_page();
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
        private final String name;

        public LCDPage(String name) {
            this.name = name;
            this.lines = new ArrayList<>(rows);
            clear();
        }

        public String getName() {
            return name;
        }

        public void setLine(int num, String text) {
            lines.set(num, text);
        }

        public String getLine(int num) {
            /* replace timers with their variables, if present */
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


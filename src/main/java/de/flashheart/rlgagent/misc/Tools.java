package de.flashheart.rlgagent.misc;

import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

@Log4j2
public class Tools {
    public static Color gold7 = new Color(0xff, 0xaa, 0x00);
    public static Color darkorange = new Color(0xff, 0x8c, 0x00);
    private static final int WIFI_PERFECT = -30;
    private static final int WIFI_EXCELLENT = -50;
    private static final int WIFI_GOOD = -60;
    private static final int WIFI_FAIR = -67;
    private static final int WIFI_MINIMUM = -70;
    private static final int WIFI_UNSTABLE = -80;
    private static final int WIFI_BAD = -90;
    private static final int NO_WIFI = -99;
    public static final String[] WIFI = new String[]{"NO_WIFI", "BAD", "FAIR", "GOOD", "PERFECT"};

    // http://www.mkyong.com/java/how-to-detect-os-in-java-systemgetpropertyosname/
    public static boolean isArm() {
        String os = System.getProperty("os.arch").toLowerCase();
        return (os.indexOf("arm") >= 0);
    }

    // http://www.mkyong.com/java/how-to-detect-os-in-java-systemgetpropertyosname/
    public static boolean isWindows() {

        String os = System.getProperty("os.name").toLowerCase();
        //windows
        return (os.indexOf("win") >= 0);

    }

    // http://www.mkyong.com/java/how-to-detect-os-in-java-systemgetpropertyosname/
    public static boolean isMac() {

        String os = System.getProperty("os.name").toLowerCase();
        //Mac
        return (os.indexOf("mac") >= 0);

    }

    // http://www.mkyong.com/java/how-to-detect-os-in-java-systemgetpropertyosname/
    public static boolean isUnix() {

        String os = System.getProperty("os.name").toLowerCase();
        //linux or unix
        return (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0);

    }

    public static String getWorkingPath(String folder) {
        return (isArm() ? "/home/pi" : System.getProperty("user.home")) + File.separator + folder;
    }

    // https://stackoverflow.com/questions/3760152/split-string-to-equal-length-substrings-in-java
    public static String[] splitInParts(String s, int partLength) {
        int len = s.length();

        // Number of parts
        int nparts = (len + partLength - 1) / partLength;
        String parts[] = new String[nparts];

        // Break into parts
        int offset = 0;
        int i = 0;
        while (i < nparts) {
            parts[i] = s.substring(offset, Math.min(offset + partLength, len));
            offset += partLength;
            i++;
        }

        return parts;
    }

    public static String left(String text, int size) {
        return left(text, size, "...");
    }

    public static String left(String text, int size, String abrev) {
        //        OPDE.debug("IN: " + text);
        int originalLaenge = text.length();
        int max = Math.min(size, originalLaenge);
        text = text.substring(0, max);
        if (max < originalLaenge) {
            text += abrev;
        }
        return text;
    }

    // https://stackoverflow.com/questions/4672271/reverse-opposing-colors
    public static Color getContrastColor(Color color) {
        double y = (299 * color.getRed() + 587 * color.getGreen() + 114 * color.getBlue()) / 1000;
        return y >= 128 ? Color.black : Color.white;
    }

    /**
     * Creates a Color object according to the names of the Java color constants. A HTML color string like "62A9FF" may
     * also be used. Please remove the leading "#".
     *
     * @param colornameOrHTMLCode
     * @return the desired color. Defaults to BLACK, in case of an error.
     */
    public static Color getColor(String colornameOrHTMLCode) {
        Color color = Color.black;

        if (colornameOrHTMLCode.equalsIgnoreCase("red")) {
            color = Color.red;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("blue")) {
            color = Color.blue;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("dark_red")) {
            color = Color.red.darker();
        } else if (colornameOrHTMLCode.equalsIgnoreCase("green")) {
            color = Color.green;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("dark_green")) {
            color = Color.green.darker();
        } else if (colornameOrHTMLCode.equalsIgnoreCase("yellow")) {
            color = Color.yellow;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("cyan")) {
            color = Color.CYAN;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("light_gray")) {
            color = Color.LIGHT_GRAY;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("dark_gray")) {
            color = Color.DARK_GRAY;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("gray")) {
            color = Color.GRAY;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("pink")) {
            color = Color.PINK;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("magenta")) {
            color = Color.MAGENTA;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("white")) {
            color = Color.WHITE;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("orange")) {
            color = gold7;
        } else if (colornameOrHTMLCode.equalsIgnoreCase("dark_orange")) {
            color = darkorange;
        } else {
            colornameOrHTMLCode = colornameOrHTMLCode.startsWith("#") ? colornameOrHTMLCode.substring(1) : colornameOrHTMLCode;
            try {
                int red = Integer.parseInt(colornameOrHTMLCode.substring(0, 2), 16);
                int green = Integer.parseInt(colornameOrHTMLCode.substring(2, 4), 16);
                int blue = Integer.parseInt(colornameOrHTMLCode.substring(4), 16);
                color = new Color(red, green, blue);
            } catch (NumberFormatException nfe) {
                color = Color.BLACK;
            }
        }
        return color;
    }

    public static LocalDateTime from_iso8601(String iso8601) {
        return ZonedDateTime.parse(iso8601).toLocalDateTime();
    }


    public static String getWifiDriverResponse(String cmd) {
        if (!Tools.isArm()) return "desktop";

        String result = "error";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", cmd);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                log.debug("command {} returned {} ", cmd, output);
                result = output.toString();
            }
        } catch (IOException | InterruptedException io) {
            log.error(io);
        }
        return result;
    }


    public static void system_shutdown() {
        if (!Tools.isArm()) return;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            //processBuilder.command(System.getProperty("shutdown"));
            processBuilder.command("bash", "-c", "/opt/rlgagentd/shutdown.sh");
            Process process = processBuilder.start();
            process.waitFor(); // as we are shutting down, there is nothing to wait for
        } catch (IOException | InterruptedException io) {
            log.error(io);
        }
    }

    /**
     * @param driver_response shell command to get the signal strength from the wifi driver. can be in dbm or percentage
     *                        like "70/100"
     * @return 4 - excellent (or desktop), 3 - good, 2 - bad, 1 - ugly, 0 - dead
     */
    public static int getWifiQuality(String driver_response) {
        if (driver_response.equalsIgnoreCase("desktop")) return 4;
        if (driver_response.trim().isEmpty()) return 0;
        int wifiQuality;
        int wifi;
        //driver_response = "level=94";
        // some wifi drivers show the link quality in percentage (e.g. 70/100) rather than dbm
        // sometimes when driver show level=100/100 use
        // iwconfig wlan0|egrep "Signal level"|awk '{print $4}'|sed 's/[^0-9/]*//g'
        try {
            if (driver_response.contains("/")) { // link quality in percentage
                StringTokenizer tokenizer = new StringTokenizer(driver_response, "/");
                int quality = Integer.parseInt(tokenizer.nextToken());
                wifi = quality / 2 - 100;
            } else { // link quality in dbm
                wifi = Integer.parseInt(driver_response.replaceAll("[^0-9\\-]", ""));
            }
        } catch (NumberFormatException nfe) {
            wifi = 0;
        }

        if (wifi >= 0) wifiQuality = 0;
        else if (wifi > WIFI_EXCELLENT) wifiQuality = 4; // PERFECT
        else if (wifi > WIFI_GOOD) wifiQuality = 3; // good
        else if (wifi > WIFI_FAIR) wifiQuality = 2; // fair
        else if (wifi > WIFI_MINIMUM) wifiQuality = 2;  // fair
        else if (wifi > WIFI_UNSTABLE) wifiQuality = 1; // bad
        else if (wifi > WIFI_BAD) wifiQuality = 1;  // bad
        else wifiQuality = 0; // no wifi

        return wifiQuality;
    }

    public static String replaceVariables(String text, HashMap<String, String> replacements) {
        //if (replacements.isEmpty()) return text;
        // matches ${var} style words
        log.trace("var replacement with {}. before {}", replacements.toString(), text);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        log.trace("and after: {}", text);
        return text;
    }

    public static boolean ping(String address) {
        try {
            log.debug("pinging {}", address);
            InetAddress iaddress = InetAddress.getByName(address);
            boolean reachable = iaddress.isReachable(1000);
            log.debug("{} reachable: {}", address, reachable);
            return reachable;
        } catch (Exception e) {
            log.debug("ping {}", e.getMessage());
            return false;
        }
    }

}

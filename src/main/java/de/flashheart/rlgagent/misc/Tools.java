package de.flashheart.rlgagent.misc;

import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * parses a standard iwconfig to a HashMap
     *
     * @param current_network_values
     * @param iwconfig_output
     * @return
     */
    public static void getWifiParams(HashMap<String, String> current_network_values, String iwconfig_output) {
        if (!Tools.isArm()) {
            // a regular desktop has always good connection
            current_network_values.put("essid", "!DESKTOP!");
            current_network_values.put("bitrate", "super");
            current_network_values.put("txpower", "high");
            current_network_values.put("access_point", "Not-Associated");
            current_network_values.put("link", "not zelda");
            current_network_values.put("freq", "good vibes");
            current_network_values.put("powermgt", "off");
            current_network_values.put("signal", Integer.toString(WIFI_PERFECT));
            return;
        }

        iwconfig_output = iwconfig_output.replaceAll("\n|\r|\"", "");

        List<String> l = Collections.list(new StringTokenizer(iwconfig_output, " :="))
                .stream().map(token -> (String) token).collect(Collectors.toList());
        current_network_values.put("essid", l.contains("ESSID") ? l.get(l.indexOf("ESSID") + 1) : "");
        if (l.contains("Point")) {
            final int index = l.indexOf("Point");
            if (l.get(index + 1).equalsIgnoreCase("Not-Associated")) {
                current_network_values.put("access_point", "Not-Associated");
            } else {
                // reconstruct MAC Address
                String mac = "";
                for (int i = 1; i < 7; i++) {
                    mac += l.get(index + i) + ":";
                }
                current_network_values.put("access_point", mac.substring(0, 16));
            }
        }

        current_network_values.put("bitrate", l.contains("Bit") ? l.get(l.indexOf("Bit") + 2) : "");
        current_network_values.put("txpower", l.contains("Tx-Power") ? l.get(l.indexOf("Tx-Power") + 1) : "");
        current_network_values.put("link", l.contains("Quality") ? l.get(l.indexOf("Quality") + 1) : "");
        current_network_values.put("freq", l.contains("Frequency") ? l.get(l.indexOf("Frequency") + 1) : "");
        current_network_values.put("powermgt", l.contains("Management") ? l.get(l.indexOf("Management") + 1) : "");
        current_network_values.put("signal", l.contains("Signal") ? l.get(l.indexOf("Signal") + 2) : "");
    }


    public static String getIWConfig(String cmd) {
        // this is a result from a non connected raspi
        // just for test reasons. Is not used in any way.
        if (!Tools.isArm()) return "wlan0     unassociated  Nickname:\"rtl_wifi\"\n" +
                "          Mode:Managed  Access Point: Not-Associated   Sensitivity:0/0\n" +
                "          Retry:off   RTS thr:off   Fragment thr:off\n" +
                "          Power Management:off\n" +
                "          Link Quality:0  Signal level:0  Noise level:0\n" +
                "          Rx invalid nwid:0  Rx invalid crypt:0  Rx invalid frag:0\n" +
                "          Tx excessive retries:0  Invalid misc:0   Missed beacon:0";

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
                log.debug("command {} returned \n\n {} ", cmd, output);
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
     * translates a signal level to an quality rating
     *
     * @param signal_level signal level as reported from iwconfig can be in dbm or percentage like "70/100"
     * @return 4 - excellent (or desktop), 3 - good, 2 - bad, 1 - ugly, 0 - dead
     */
    public static int getWifiQuality(String signal_level) {
        log.debug("signal level {}", signal_level);
        if (signal_level.equalsIgnoreCase("desktop")) return 4;
        if (signal_level.trim().isEmpty()) return 0;
        int wifiQuality;

        //driver_response = "level=94";
        // some wifi drivers show the link quality in percentage (e.g. 70/100) rather than dbm
        // sometimes when driver show level=100/100 use
        try {
            if (signal_level.contains("/")) { // link quality in percentage
                StringTokenizer tokenizer = new StringTokenizer(signal_level, "/");
                int quality = Math.min(100, Math.abs(Integer.parseInt(tokenizer.nextToken())));
                wifiQuality = Math.min(4, quality / 25 + 1);
                log.trace("{}% quality is {}", quality, wifiQuality, WIFI[wifiQuality]);
            } else { // link quality in dbm
                int dbm = Integer.parseInt(signal_level.replaceAll("[^0-9\\-]", ""));

                if (dbm >= 0) wifiQuality = 0;
                else if (dbm > WIFI_EXCELLENT) wifiQuality = 4; // PERFECT
                else if (dbm > WIFI_GOOD) wifiQuality = 3; // good
                else if (dbm > WIFI_FAIR) wifiQuality = 2; // fair
                else if (dbm > WIFI_MINIMUM) wifiQuality = 2;  // fair
                else if (dbm > WIFI_UNSTABLE) wifiQuality = 1; // bad
                else if (dbm > WIFI_BAD) wifiQuality = 1;  // bad
                else wifiQuality = 0; // no wifi

                log.trace("{} dbm signal quality {}", dbm, WIFI[wifiQuality]);
            }
        } catch (NumberFormatException nfe) {
            wifiQuality = 0;
        }

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

//
//    public static boolean ping(String address, int openPort, int timeOutMillis) {
//        if (address.trim().isEmpty()) return false;
//        log.debug("trying socket connection to {}", address);
//        try {
//            try (Socket soc = new Socket()) {
//                soc.connect(new InetSocketAddress(address, openPort), timeOutMillis);
//            }
//            log.debug("{} is reachable", address);
//            return true;
//        } catch (IOException ex) {
//            log.debug("{} is NOT reachable", address);
//            return false;
//        }
//    }
//
//    public static boolean ping(String address, int timeout) {
//        try {
//            log.debug("pinging {}", address);
//            InetAddress iaddress = InetAddress.getByName(address);
//            boolean reachable = iaddress.isReachable(timeout);
//            log.debug("{} reachable: {}", address, reachable);
//            return reachable;
//        } catch (Exception e) {
//            log.warn("ping {}", e.getMessage());
//            return false;
//        }
//    }


    public static boolean fping(HashMap<String, String> current_network_values, String address) {
        if (address.trim().isEmpty()) return false;
        boolean success;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", "fping", "-c5", "-t500", "-q", address);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            int exitVal = process.waitFor();
            success = exitVal == 0;

            if (success) {
                List<String> tokens = Collections.list(new StringTokenizer(output.toString(), "/:="))
                        .stream().map(token -> (String) token).collect(Collectors.toList());
                if (tokens.size() >= 12) {
                    current_network_values.put("ping_loss", tokens.get(6));
                    current_network_values.put("ping_min", tokens.get(10));
                    current_network_values.put("ping_avg", tokens.get(11));
                    current_network_values.put("ping_max", tokens.get(12));
                }

                log.debug("fping returned \n\n {} ", output);
            } else {
                log.debug("fping failed to contact {}", address);
            }
        } catch (IOException | InterruptedException io) {
            log.error(io);
            success = false;
        }

        return success;

//        pi@iot02:~ $ fping -c5 -t500 -q srv01
//            srv01 : xmt/rcv/%loss = 5/5/0%, min/avg/max = 2.23/4.67/6.36
        //srv01 : xmt/rcv/%loss = 5/5/0%, min/avg/max = 0.338/0.470/0.565

    }

}

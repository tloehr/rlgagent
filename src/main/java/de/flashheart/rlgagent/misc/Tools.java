package de.flashheart.rlgagent.misc;

import de.flashheart.rlgagent.RLGAgent;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
    public static void check_iwconfig(HashMap<String, String> current_network_values, String iwconfig_output) {
        if (!Tools.isArm()) {
            // a regular desktop has always good connection
            current_network_values.put("essid", "!DESKTOP!");
            current_network_values.put("ap", "00223F97A198");
            current_network_values.put("bitrate", "--");
            current_network_values.put("txpower", "--");
            current_network_values.put("link", "--");
            current_network_values.put("freq", "--");
            current_network_values.put("powermgt", "--");
            current_network_values.put("signal", Integer.toString(WIFI_PERFECT));
            return;
        }

        iwconfig_output = iwconfig_output.replaceAll("\n|\r|\"", "");

        List<String> l = Collections.list(new StringTokenizer(iwconfig_output, " :="))
                .stream().map(token -> (String) token).collect(Collectors.toList());
        current_network_values.put("essid", l.contains("ESSID") ? l.get(l.indexOf("ESSID") + 1) : "--");
        if (l.contains("Point")) {
            final int index = l.indexOf("Point");
            if (l.get(index + 1).equalsIgnoreCase("Not-Associated")) {
                current_network_values.put("ap", "Not-Associated");
            } else {
                // reconstruct MAC Address
                String mac = "";
                for (int i = 1; i < 7; i++) {
                    mac += l.get(index + i);
                }
                current_network_values.put("ap", mac);
            }
        }

        current_network_values.put("bitrate", l.contains("Bit") ? l.get(l.indexOf("Bit") + 2) : "--");
        current_network_values.put("txpower", l.contains("Tx-Power") ? l.get(l.indexOf("Tx-Power") + 1) : "--");
        current_network_values.put("link", l.contains("Quality") ? l.get(l.indexOf("Quality") + 1) : "--");
        current_network_values.put("freq", l.contains("Frequency") ? l.get(l.indexOf("Frequency") + 1) : "--");
        current_network_values.put("powermgt", l.contains("Management") ? l.get(l.indexOf("Management") + 1) : "--");
        current_network_values.put("signal", l.contains("Signal") ? l.get(l.indexOf("Signal") + 2) : "--");
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
                log.trace("command {} returned \n\n {} ", cmd, output);
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
        log.trace("signal level {}", signal_level);
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

    public static String replaceVariables(String text, Map<String, String> replacements) {
        log.trace("var replacement with {}. before {}", replacements.toString(), text);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        log.trace("and after: {}", text);
        return text;
    }

//    public static boolean  nmap(HashMap<String, String> current_network_values, String address, int tries, int timeout) {
//
//    }


    /**
     * does a fping to the specific address. stores the statistics in the provided map
     *
     * @param current_network_values map to store the statistics into
     * @param address                the host(s) to be checked
     * @param tries                  number of pings - parameter for fping
     * @param timeout                timeout in ms before failing - parameter for fping
     * @return true if address was reached
     */
    public static boolean fping(HashMap<String, String> current_network_values, String address, int tries, int timeout) {
        if (address.trim().isEmpty()) return false;
        boolean success;
        try {
            // todo: complain about missing fping or retreat to ping
            // what about windows ?
            ProcessBuilder processBuilder = new ProcessBuilder();
            String cmd = String.format("fping -c%d -t%d -q %s", tries, timeout, address);
            processBuilder.command("bash", "-c", cmd);

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream())); // fping uses Stderr rather than stdout

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            /*
             Exit status is 0 if all the hosts are reachable, 1 if some hosts were unreachable, 2 if any IP addresses
             were not found, 3 for invalid command line arguments, and 4 for a system call failure.
             */
            int exitVal = process.waitFor();
            success = exitVal == 0;

            current_network_values.put("ping_host", address);
            current_network_values.put("ping_success", success ? "ok" : "failed");
            current_network_values.put("last_ping", LocalDateTime.now().format(RLGAgent.myformat));
            if (success) {
                List<String> tokens = Collections.list(new StringTokenizer(output.toString(), "/:=,"))
                        .stream().map(token -> (String) token).collect(Collectors.toList());
                if (tokens.size() >= 12) {
                    current_network_values.put("ping_loss", tokens.get(6).trim());
                    current_network_values.put("ping_min", tokens.get(10).trim());
                    current_network_values.put("ping_avg", tokens.get(11).trim());
                    current_network_values.put("ping_max", tokens.get(12).trim());
                }
                log.trace("fping returned \n\n {} - return code {}", output, exitVal);
            } else {
                current_network_values.put("ping_loss", "100%");
                current_network_values.put("ping_min", "--");
                current_network_values.put("ping_avg", "--");
                current_network_values.put("ping_max", "--");
                log.trace("fping returned \n\n {} - return code {}", output, exitVal);
            }

        } catch (IOException | InterruptedException io) {
            log.error(io);
            success = false;
        }

        return success;

    }

    public static String get_ipaddress(String cmd) {
        if (!Tools.isArm()) return "1.2.3.4/24";

        String result = "no_ip_info";

        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", cmd);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exitVal = process.waitFor();
            if (exitVal == 0 && !output.toString().trim().isEmpty()) {
                log.trace("command {} returned \n\n {} ", cmd, output);
                result = output.toString();
            }
        } catch (IOException | InterruptedException io) {
            log.error(io);
        }
        return result;
    }

    public static String getGametimeBlinkingScheme(LocalDateTime remainingTime) {
        String scheme = "infty:on,75;off,75;on,75;off,1000;";
        log.trace("remaining time for blinking scheme: {}", remainingTime);
        int minutes = remainingTime.getMinute();

        int hours = remainingTime.getHour();
        int tenminutes = minutes / 10;
        int remminutes = minutes - tenminutes * 10; // restliche Minuten ausrechnen

        if (hours > 0 || minutes > 0) {
            if (hours > 0) {
                for (int h = 0; h < hours; h++) {
                    scheme += "on,1000;off,250;";
                }
            }

            if (tenminutes > 0) {
                for (int tm = 0; tm < tenminutes; tm++) {
                    scheme += "on,625;off,250;";
                }
            }

            if (remminutes > 0) {
                for (int rm = 0; rm < remminutes; rm++) {
                    scheme += "on,250;off,250;";
                }
            }

            scheme += "off,2500;";

        } else {
            scheme += "on,100;off,250;";
        }

        log.debug("time blinking scheme: {}", scheme);

        return scheme;
    }

    public static JSONObject merge(JSONObject... jsons) {
        HashMap<String, Object> map = new HashMap<>();
        Arrays.stream(jsons).forEach(json -> map.putAll(json.toMap()));
        return new JSONObject(map);
    }


}

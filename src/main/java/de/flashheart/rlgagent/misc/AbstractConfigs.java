package de.flashheart.rlgagent.misc;


import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.properties.SortedProperties;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

@Log4j2
public abstract class AbstractConfigs {
    public static final String SHUTDOWN_COMMAND_LINE = "shutdown_cmd_line";
    public static final String LOGLEVEL = "loglevel";
    public static final String MYUUID = "uuid";

    protected final String CONFIGFILE;
    protected final String WORKINGPATH;
    protected final SortedProperties configs;
    protected final Properties buildContext;

    public abstract String getProjectName();

    public abstract void loadDefaults();

    public String getWORKINGPATH() {
        return WORKINGPATH;
    }

    protected AbstractConfigs(String workingpath) throws IOException {
        WORKINGPATH = workingpath;
        CONFIGFILE = WORKINGPATH + File.separator + "config.txt";

        configs = new SortedProperties(); // Einstellungen, die verändert werden
        buildContext = new Properties(); // inhalte der build.properties (von Maven)


        // configdatei einlesen
        loadDefaults(); // defaults from the inheriting classes
        loadConfigs();
        loadBuildContext();

        if (!configs.containsKey(MYUUID)) {
            configs.put(MYUUID, UUID.randomUUID().toString());
        }

    }

    private void loadBuildContext() throws IOException {
        InputStream in2 = AbstractConfigs.class.getResourceAsStream("/build.properties");
        buildContext.load(in2);
        in2.close();
        if (buildContext.containsKey("timestamp")) {
            SimpleDateFormat sdfmt = new SimpleDateFormat("yyyyMMddHHmm");

            System.out.println(sdfmt.format(new Date())); // Mittwoch, 21. März 2007 09:14
            Date buildDate = new Date(Long.parseLong(buildContext.getProperty("timestamp")));
            buildContext.put("buildDate", sdfmt.format(buildDate));
        }
    }

    private void loadConfigs() throws IOException {
        File configFile = new File(CONFIGFILE);
        configFile.getParentFile().mkdirs();
        configFile.createNewFile(); // falls nicht vorhanden

        FileInputStream in = new FileInputStream(configFile);
        Properties p = new SortedProperties();
        p.load(in);
        configs.putAll(p);
        p.clear();
        in.close();
    }

    public void put(Object key, Object value) {
        configs.put(key, value.toString());
        saveConfigs();
    }

    public String getBuildInfo(Object key) {
        return buildContext.containsKey(key) ? buildContext.get(key).toString() : "null";
    }

    public boolean is(Object key) {
        return Boolean.parseBoolean(configs.containsKey(key) ? configs.get(key).toString() : "false");
    }

    public int getInt(Object key) {
        return Integer.parseInt(configs.containsKey(key) ? configs.get(key).toString() : "-1");
    }

    public long getLong(Object key) {
        return Long.parseLong(configs.containsKey(key) ? configs.get(key).toString() : "-1");
    }

    public String get(String key) {
        return configs.getProperty(key, "null");
    }

    public String get(String key, String defaultValue) {
        return configs.getProperty(key, defaultValue);
    }

    public void saveConfigs() {
        try {
            File configFile = new File(CONFIGFILE);
            FileOutputStream out = new FileOutputStream(configFile);
            configs.store(out, "Settings " + getProjectName());
            out.close();
        } catch (Exception ex) {
            log.debug(ex);
            System.exit(0);
        }
    }

}

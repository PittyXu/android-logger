package com.noveogroup.android.log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * THe logger manager.
 * <p/>
 * To configure this logger manager you can include an
 * {@code android-logger.properties} file in src directory.
 * The format of configuration file is:
 * <pre>
 * # root logger configuration
 * root=&lt;level&gt;:&lt;tag&gt;
 * # package / class logger configuration
 * logger.&lt;package or class name&gt;=&lt;level&gt;:&lt;tag&gt;
 * </pre>
 * For example, the following configuration will
 * log all ERROR messages with tag "MyApplication" and all
 * messages from classes {@code com.example.server.*} with
 * tag "MyApplication-server":
 * <pre>
 * root=ERROR:MyApplication
 * logger.com.example.server=DEBUG:MyApplication-server
 * </pre>
 * <p/>
 */
public final class LoggerManager {

    private LoggerManager() {
        throw new UnsupportedOperationException();
    }

    private static final String TAG = "XXX";

    /**
     * Debug logger that not managed by configuration.
     * <p/>
     * It is recommended to use this logger for debugging purposes only.
     * It is useful to add static import to make logging calls shorter.
     */
    public static final Logger LOG = new SimpleLogger(TAG, Logger.Level.VERBOSE);

    private static final String PROPERTIES_NAME = "android-logger.properties";
    private static final String CONF_ROOT = "root";
    private static final String CONF_LOGGER = "logger.";
    private static final Pattern CONF_LOGGER_REGEX = Pattern.compile("(.*?):(.*)");
    private static final Logger.Level CONF_DEFAULT_LEVEL = Logger.Level.INFO;
    private static final Map<String, Logger> loggerMap;

    private static void loadProperties(Properties properties) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = LoggerManager.class.getClassLoader().getResourceAsStream(PROPERTIES_NAME);
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(PROPERTIES_NAME);
                if (inputStream != null) {
                    properties.load(inputStream);
                }
            }
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static Logger decodeLogger(String loggerString) {
        Matcher matcher = CONF_LOGGER_REGEX.matcher(loggerString);
        if (matcher.matches()) {
            String levelString = matcher.group(1);
            String tag = matcher.group(2);
            try {
                return new SimpleLogger(tag, Logger.Level.valueOf(levelString));
            } catch (IllegalArgumentException e) {
                return new SimpleLogger(loggerString, CONF_DEFAULT_LEVEL);
            }
        } else {
            return new SimpleLogger(loggerString, CONF_DEFAULT_LEVEL);
        }
    }

    private static Map<String, Logger> loadConfiguration() {
        Map<String, Logger> loggerMap = new HashMap<String, Logger>();

        Properties properties = new Properties();
        try {
            loadProperties(properties);
        } catch (IOException e) {
            LOG.e(String.format("Cannot configure logger from %s. Default configuration will be used", PROPERTIES_NAME), e);
            loggerMap.put(null, LOG);
            return loggerMap;
        }

        if (properties.stringPropertyNames().isEmpty()) {
            LOG.e("Logger configuration file is empty. Default configuration will be used");
            loggerMap.put(null, LOG);
            return loggerMap;
        }

        for (String propertyName : properties.stringPropertyNames()) {
            String propertyValue = properties.getProperty(propertyName);

            if (propertyName.equals(CONF_ROOT)) {
                loggerMap.put(null, decodeLogger(propertyValue));
            }
            if (propertyName.startsWith(CONF_LOGGER)) {
                String loggerName = propertyName.substring(CONF_LOGGER.length());
                loggerMap.put(loggerName, decodeLogger(propertyValue));
            }
        }
        return loggerMap;
    }


    static {
        loggerMap = Collections.unmodifiableMap(loadConfiguration());
    }

    private static Logger findLogger(String name) {
        String currentKey = null;
        if (name != null) {
            for (String key : loggerMap.keySet()) {
                if (key != null && name.startsWith(key)) {
                    if (currentKey == null || currentKey.length() < key.length()) {
                        currentKey = key;
                    }
                }
            }
        }
        Logger logger = loggerMap.get(currentKey);
        return logger != null ? logger : new SimpleLogger("", Logger.Level.VERBOSE);
    }

    /**
     * Root logger that has {@code null} as a name.
     */
    public static final Logger ROOT = getLogger((String) null);

    /**
     * Returns logger corresponding to the specified class.
     *
     * @param aClass the class.
     * @return the {@link Logger} implementation.
     */
    public static Logger getLogger(Class<?> aClass) {
        return findLogger(aClass == null ? null : aClass.getName());
    }

    /**
     * Returns logger corresponding to the specified name.
     *
     * @param name the name.
     * @return the {@link Logger} implementation.
     */
    public static Logger getLogger(String name) {
        return findLogger(name);
    }

    /**
     * Returns logger corresponding to the caller class.
     *
     * @return the {@link Logger} implementation.
     */
    public static Logger getLogger() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length < 3) {
            throw new RuntimeException("unexpected stack trace");
        } else {
            StackTraceElement stackTraceElement = stackTrace[2];
            return findLogger(stackTraceElement.getClassName());
        }
    }

}
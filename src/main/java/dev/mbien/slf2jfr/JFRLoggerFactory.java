package dev.mbien.slf2jfr;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByKey;

/**
 *
 * @author mbien
 */
public class JFRLoggerFactory implements ILoggerFactory {
    
    private static final JFRLoggerFactory INSTANCE;
    
    private static final Map<String, String> settings;
    private static final Map<String, JFRLogger> cache;
    
    private static final AbstractJFRLoggerFactory factory;
    
    private static final String PREFIX;
    private static final String DEVAULT_LEVEL;
    
    static {
        PREFIX = "jfrlog.";

        Map<String, String> map = new HashMap<>();
        
        ((Properties)System.getProperties().clone()).forEach((key, value) -> {
            String keyStr = (String)key;
            if(keyStr.startsWith(PREFIX)) {
                String valueStr = ((String)value).toLowerCase();
                map.put(keyStr.substring(PREFIX.length()), valueStr);
            }
        });
        
        boolean loggerCache = false;
        boolean recordOrigin = true;
        String defaultLevel = "trace";
        
        if(map.isEmpty()) {
            settings = Collections.emptyMap();
        }else{
            
            loggerCache  = parseBoolean(map.remove("loggerCache"), loggerCache);
            recordOrigin = parseBoolean(map.remove("recordOrigin"), recordOrigin);
            String level = map.remove("default");
            
            if(level != null && !level.isBlank()) {
                defaultLevel = level;
            }
            
            // reverse order so that we have the most specific setting first
            Map<String, String> reverseSorted = new LinkedHashMap<>(map.size());
            map.entrySet().stream()
                    .sorted(comparingByKey(reverseOrder()))
                    .forEach((entry) -> {
                        reverseSorted.put(entry.getKey(), entry.getValue());
                    });
            
            settings = Collections.unmodifiableMap(reverseSorted);
        }
        
        DEVAULT_LEVEL = defaultLevel;

        if(recordOrigin) {
            factory = new OriginTrackingLoggerFactory();
            if(loggerCache) {
                cache = new ConcurrentHashMap<String, JFRLogger>(64);
            }else{
                cache = null;
            }
        }else{
            factory = new NoOriginLoggerFactory();
            cache = null;
        }
        
        INSTANCE = new JFRLoggerFactory();
        
//        settings.entrySet().forEach(System.out::println);
        
    }

    private JFRLoggerFactory() {}
    
    public static JFRLoggerFactory getFactory() {
        return INSTANCE;
    }
    
    private static boolean parseBoolean(String value, boolean defaultValue) {
        if(value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
 
    private static JFRLogger getLoggerForLevel(String name, String level) {
        switch(level) {
            case "all":
            case "trace": return factory.getTrace(name);
            case "debug": return factory.getDebug(name);
            case "info":  return factory.getInfo(name);
            case "warn":  return factory.getWarn(name);
            case "error": return factory.getError(name);
            case "off":   return factory.getNoOp(name);
            default:      return factory.getTrace(name);
        }
    }

    
    @Override
    public Logger getLogger(String name) {
        
        if(cache != null) {
            JFRLogger cached = cache.get(name);
            if(cached != null) {
                return cached;
            }
        }
        
        String level = settings.get(name);
        if(level == null || level.isEmpty()) {
            for (Map.Entry<String, String> setting : settings.entrySet()) {
                if(name.startsWith(setting.getKey())) {
                    level = setting.getValue();
                    break;
                }
            }
        }
        
        if(level == null) {
            level = DEVAULT_LEVEL;
        }
        
        JFRLogger logger = getLoggerForLevel(name, level);
        if (cache != null) {
            cache.put(name, logger);
        }
        
        return logger;
    }

    public boolean isLoggerCacheEnabled() {
        return cache != null;
    }
    
    public boolean isRecordOriginEnabled() {
        return factory instanceof OriginTrackingLoggerFactory;
    }
    
    
    private static abstract class AbstractJFRLoggerFactory {
        abstract JFRLogger getTrace(String name);
        abstract JFRLogger getDebug(String name);
        abstract JFRLogger getInfo(String name);
        abstract JFRLogger getWarn(String name);
        abstract JFRLogger getError(String name);
        abstract JFRLogger getNoOp(String name);
    }
    
    private static class NoOriginLoggerFactory extends AbstractJFRLoggerFactory {
        
        private static final JFRLogger TRACE_LOGGER = new JFRLogger.Trace();
        private static final JFRLogger DEBUG_LOGGER = new JFRLogger.Debug();
        private static final JFRLogger INFO_LOGGER = new JFRLogger.Info();
        private static final JFRLogger WARN_LOGGER = new JFRLogger.Warn();
        private static final JFRLogger ERROR_LOGGER = new JFRLogger.Error();
        private static final JFRLogger NOOP_LOGGER = new JFRLogger();

        @Override JFRLogger getTrace(String name) { return TRACE_LOGGER; }
        @Override JFRLogger getDebug(String name) { return DEBUG_LOGGER; }
        @Override JFRLogger getInfo(String name) { return INFO_LOGGER; }
        @Override JFRLogger getWarn(String name) { return WARN_LOGGER; }
        @Override JFRLogger getError(String name) { return ERROR_LOGGER; }
        @Override JFRLogger getNoOp(String name) { return NOOP_LOGGER; }
        
    }
    
    private static class OriginTrackingLoggerFactory extends AbstractJFRLoggerFactory {
        @Override JFRLogger getTrace(String name) { return new JFRLogger.Trace(name); }
        @Override JFRLogger getDebug(String name) { return new JFRLogger.Debug(name); }
        @Override JFRLogger getInfo(String name) { return new JFRLogger.Info(name); }
        @Override JFRLogger getWarn(String name) { return new JFRLogger.Warn(name); }
        @Override JFRLogger getError(String name) { return new JFRLogger.Error(name); }
        @Override JFRLogger getNoOp(String name) { return new JFRLogger(name); }
    }
    
}

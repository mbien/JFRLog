package dev.mbien.slf2jfr;

import java.time.Duration;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author mbien
 */
public class JFRLoggerTest {
    

    @BeforeAll
    public void testInit() {
        System.setProperty("jfrlog.tracelogger", "trace");
        System.setProperty("jfrlog.debuglogger", "debug");
        System.setProperty("jfrlog.infologger", "info");
        System.setProperty("jfrlog.warnlogger", "warn");
        System.setProperty("jfrlog.errorlogger", "error");
        System.setProperty("jfrlog.noop", "off");
    }
    
    @Test
    public void testLoggerFactory() {
        
        assertNotNull(LoggerFactory.getILoggerFactory());
        assertEquals(JFRLoggerFactory.getFactory(), LoggerFactory.getILoggerFactory());
        
        {
            Logger log = LoggerFactory.getLogger("tracelogger");
            assertNotNull(log);
            assertEquals(JFRLogger.Trace.class.getName(), log.getClass().getName());
        }
        {
            Logger log = LoggerFactory.getLogger("debuglogger");
            assertNotNull(log);
            assertEquals(JFRLogger.Debug.class.getName(), log.getClass().getName());
        }
        {
            Logger log = LoggerFactory.getLogger("infologger");
            assertNotNull(log);
            assertEquals(JFRLogger.Info.class.getName(), log.getClass().getName());
        }
        {
            Logger log = LoggerFactory.getLogger("warnlogger");
            assertNotNull(log);
            assertEquals(JFRLogger.Warn.class.getName(), log.getClass().getName());
        }
        {
            Logger log = LoggerFactory.getLogger("errorlogger");
            assertNotNull(log);
            assertEquals(JFRLogger.Error.class.getName(), log.getClass().getName());
        }
        {
            Logger log = LoggerFactory.getLogger("noop");
            assertNotNull(log);
            assertEquals(JFRLogger.class.getName(), log.getClass().getName());
        }
        
        {
            Logger log = LoggerFactory.getLogger("some.other.logger");
            assertNotNull(log);
            assertEquals(JFRLogger.Trace.class.getName(), log.getClass().getName());
        }
        
    }
    
    @Test
    public void testBasicLog() {

        Logger log = LoggerFactory.getLogger("tracelogger");
        
        assertNotNull(log);
        assertEquals(JFRLogger.Trace.class.getName(), log.getClass().getName());
        
        try (EventStream es = new RecordingStream()) {
            
            String message = "hello";
            
            es.onEvent("log.Trace", (e) -> {
                System.out.println(e);
                assertEquals(message + " trace", e.getString("message"));
                assertEquals(null, e.getString("throwable"));
            });
            es.onEvent("log.Debug", (e) -> {
                System.out.println(e);
                assertEquals(message + " debug", e.getString("message"));
                assertEquals(null, e.getString("throwable"));
            });
            es.onEvent("log.Info", (e) -> {
                System.out.println(e);
                assertEquals(message + " info", e.getString("message"));
                assertEquals(null, e.getString("throwable"));
            });
            es.onEvent("log.Warn", (e) -> {
                System.out.println(e);
                assertEquals(message + " warn", e.getString("message"));
                assertEquals(null, e.getString("throwable"));
            });
            es.onEvent("log.Error", (e) -> {
                System.out.println(e);
                assertEquals(message + " error", e.getString("message"));
                assertEquals(null, e.getString("throwable"));
                es.close();
            });
            es.startAsync();

            log.trace(message + " trace");
            log.debug(message + " debug");
            log.info(message + " info");
            log.warn(message + " warn");
            log.error(message + " error");

            try {
                es.awaitTermination(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                fail(ex);
            }
        }
        
    }
    
    @Test
    public void testExceptionLog() {

        Logger log = LoggerFactory.getLogger("errorlogger");
        assertNotNull(log);
        assertEquals(JFRLogger.Error.class.getName(), log.getClass().getName());
        
        try (EventStream es = new RecordingStream()) {
            
            String message = "oh dear";
            
            es.onEvent("log.Error", (e) -> {
                System.out.println(e);
                assertEquals(message, e.getString("message"));
                assertNotNull(e.getString("throwable"));
                es.close();
            });
            es.startAsync();

            try{
                throw new RuntimeException("don't panic");
            }catch(RuntimeException ex) {
                log.error(message, ex);
            }

            try {
                es.awaitTermination(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                fail(ex);
            }
        }
        
    }
    
    @Test
    public void testFormattedLog() {

        Logger log = LoggerFactory.getLogger("infologger");
        assertNotNull(log);
        assertEquals(JFRLogger.Info.class.getName(), log.getClass().getName());
        
        try (EventStream es = new RecordingStream()) {
            
            String message = "My name is: hans wurst";
            
            es.onEvent("log.Info", (e) -> {
                System.out.println(e);
                assertEquals(message, e.getString("message"));
                assertNull(e.getString("throwable"));
            });
            es.onEvent("log.Warn", (e) -> {
                System.out.println(e);
                assertEquals(message, e.getString("message"));
                assertNull(e.getString("throwable"));
                es.close();
            });
            es.startAsync();

            log.info("My name is: %s %s", "hans", "wurst");
            log.info("My name is: {} {}", "hans", "wurst");
            log.warn("My name is: {0} {1}", "hans", "wurst");

            try {
                es.awaitTermination(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                fail(ex);
            }
        }
        
    }
    
    @Test
    public void testInfo() {

        Logger log = LoggerFactory.getLogger("infologger");
        
        assertNotNull(log);
        assertEquals(JFRLogger.Info.class.getName(), log.getClass().getName());
        
        try (EventStream es = new RecordingStream()) {
            
            String message = "info-test";
            
            es.onEvent("log.Trace", (e) -> {
                fail();
            });
            es.onEvent("log.Debug", (e) -> {
                fail();
            });
            es.onEvent("log.Info", (e) -> {
                System.out.println(e);
                es.close();
            });
            es.onEvent("log.Warn", (e) -> {
                System.out.println(e);
            });
            es.onEvent("log.Error", (e) -> {
                System.out.println(e);
            });
            es.startAsync();

            log.trace(message + " trace");
            log.debug(message + " debug");

            log.warn(message + " warn");
            log.error(message + " error");
            log.info(message + " info");

            try {
                es.awaitTermination(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                fail(ex);
            }
        }
        
    }

    
}

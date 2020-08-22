package dev.mbien.slf2jfr;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.Format;
import java.text.MessageFormat;
import java.util.Locale;
import jdk.jfr.EventType;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

/**
 * Records log messages as JFR events.
 * 
 * @author mbien
 */
public class JFRLogger extends MarkerIgnoringBase {

    private static final Locale locale = Locale.getDefault(Locale.Category.FORMAT);
    private static final String ARG_PLACEHOLDER = "{}";
    
    JFRLogger() {
        this(null);
    }
    
    JFRLogger(String name) {
        this.name = name;
    }
    
    private static String throwableToString(Throwable t) {
        if (t == null) return null;
        StringWriter sw = new StringWriter(1024);
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // slf4j is supposed to support three different msg formats at the same time
    // without knowing which format is in the string
    //  - the slf4j format is based on simple {} placeholders
    //  - MessageFormat uses indexed placeholders i.e. {0} {1}
    //  - and String.format uses %x tokens
    // TODO this is a bit hacky right now
    private static String format(String format, Object arg) {
        
        if(format.contains(ARG_PLACEHOLDER)) {
            return MessageFormatter.format(format, arg).getMessage();
        }

        try{
            MessageFormat mf = new MessageFormat(format, locale);
            Format[] formats = mf.getFormats();
            if (formats != null && formats.length > 0) {
                return mf.format(arg);
            }
        }catch(Exception ignored) {}
        
        try{
            return String.format(locale, format, arg);
        }catch(Exception ignored) {}
        
        return format;
    }
    
    private static String format(String format, Object arg1, Object arg2) {

        if(format.contains(ARG_PLACEHOLDER)) {
            return MessageFormatter.format(format, arg1, arg2).getMessage();
        }
        
        try{
            MessageFormat mf = new MessageFormat(format, locale);
            Format[] formats = mf.getFormats();
            if (formats != null && formats.length > 0) {
                return mf.format(new Object[] {arg1, arg2});
            }
        }catch(Exception igmored) {}
        
        try{
            return String.format(locale, format, arg1, arg2);
        }catch(Exception ignored) {}
        
        return format;
    }

    private static String format(String format, Object[] args) {
        
        if(format.contains(ARG_PLACEHOLDER)) {
            return MessageFormatter.arrayFormat(format, args).getMessage();
        }

        try{
            MessageFormat mf = new MessageFormat(format, locale);
            Format[] formats = mf.getFormats();
            if (formats != null && formats.length > 0) {
                return mf.format(args);
            }
        }catch(Exception ignored) {}
        
        try{
            return String.format(locale, format, args);
        }catch(Exception ignored) {}
        
        return format;
    }
    
    
    private void record(JFRLogEvent event, String msg) {
        event.origin = name;
        event.message = msg;
        event.commit();
    }
    
    private void record(JFRLogEvent event, String format, Object arg1) {
        event.origin = name;
        event.message = format(format, arg1);
        event.commit();
    }
    
    private void record(JFRLogEvent event, String msg, Throwable throwable) {
        event.origin = name;
        event.message = msg;
        try{
            event.throwable = throwableToString(throwable);
        }finally{
            event.commit();
        }
    }
    
    // special case for next two methods: if last arg is throwable -> record it as such
    private void record(JFRLogEvent event, String format, Object arg1, Object arg2) {
        try{
            event.origin = name;
            if(arg2 instanceof Throwable) {
                event.message = format(format, arg1);
                event.throwable = throwableToString((Throwable) arg2);
            }else{
                event.message = format(format, arg1, arg2);
            }
        }finally{
            event.commit();
        }
    }
    
    private void record(JFRLogEvent event, String format, Object... args) {
        if(args.length == 0) {
            record(event, format);
        }else{
            try{
                event.origin = name;
                if(args[args.length-1] instanceof Throwable) {
                    event.message = format(format, args);
                    event.throwable = throwableToString((Throwable) args[args.length-1]);
                }else{
                    event.message = format(format, args);
                }
            }finally{
                event.commit();
            }
        }
    }
    
    
    @Override
    public final void trace(String msg) {
        if(isTraceEnabled()) record(new JFRLogEvent.Trace(), msg);
    }

    @Override
    public final void trace(String format, Object arg1) {
        if(isTraceEnabled()) record(new JFRLogEvent.Trace(), format, arg1);
    }

    @Override
    public final void trace(String format, Object arg1, Object arg2) {
        if(isTraceEnabled()) record(new JFRLogEvent.Trace(), format, arg1, arg2);
    }

    @Override
    public final void trace(String format, Object... args) {
        if(isTraceEnabled()) record(new JFRLogEvent.Trace(), format, args);
    }

    @Override
    public final void trace(String msg, Throwable t) {
        if(isTraceEnabled()) record(new JFRLogEvent.Trace(), msg, t);
    }

        
    @Override
    public final void debug(String msg) {
        if(isDebugEnabled()) record(new JFRLogEvent.Debug(), msg);
    }

    @Override
    public final void debug(String format, Object arg1) {
        if(isDebugEnabled()) record(new JFRLogEvent.Debug(), format, arg1);
    }

    @Override
    public final void debug(String format, Object arg1, Object arg2) {
        if(isDebugEnabled()) record(new JFRLogEvent.Debug(), format, arg1, arg2);
    }

    @Override
    public final void debug(String format, Object... args) {
        if(isDebugEnabled()) record(new JFRLogEvent.Debug(), format, args);
    }

    @Override
    public final void debug(String msg, Throwable t) {
        if(isDebugEnabled()) record(new JFRLogEvent.Debug(), msg, t);
    }

    
    @Override
    public final void info(String msg) {
        if(isInfoEnabled()) record(new JFRLogEvent.Info(), msg);
    }

    @Override
    public final void info(String format, Object arg1) {
        if(isInfoEnabled()) record(new JFRLogEvent.Info(), format, arg1);
    }

    @Override
    public final void info(String format, Object arg1, Object arg2) {
        if(isInfoEnabled()) record(new JFRLogEvent.Info(), format, arg1, arg2);
    }

    @Override
    public final void info(String format, Object... args) {
        if(isInfoEnabled()) record(new JFRLogEvent.Info(), format, args);
    }

    @Override
    public final void info(String msg, Throwable t) {
        if(isInfoEnabled()) record(new JFRLogEvent.Info(), msg, t);
    }

    
    @Override
    public final void warn(String msg) {
        if(isWarnEnabled()) record(new JFRLogEvent.Warn(), msg);
    }

    @Override
    public final void warn(String format, Object arg1) {
        if(isWarnEnabled()) record(new JFRLogEvent.Warn(), format, arg1); 
    }

    @Override
    public final void warn(String format, Object arg1, Object arg2) {
        if(isWarnEnabled()) record(new JFRLogEvent.Warn(), format, arg1, arg2);
    }

    @Override
    public final void warn(String format, Object... args) {
        if(isWarnEnabled()) record(new JFRLogEvent.Warn(), format, args);
    }

    @Override
    public final void warn(String msg, Throwable t) {
        if(isWarnEnabled()) record(new JFRLogEvent.Warn(), msg, t);
    }


    @Override
    public final void error(String msg) {
        if(isErrorEnabled()) record(new JFRLogEvent.Error(), msg);
    }

    @Override
    public final void error(String format, Object arg1) {
        if(isErrorEnabled()) record(new JFRLogEvent.Error(), format, arg1); 
    }

    @Override
    public final void error(String format, Object arg1, Object arg2) {
        if(isErrorEnabled()) record(new JFRLogEvent.Error(), format, arg1, arg2);
    }

    @Override
    public final void error(String format, Object... args) {
        if(isErrorEnabled()) record(new JFRLogEvent.Error(), format, args);
    }

    @Override
    public final void error(String msg, Throwable t) {
        if(isErrorEnabled()) record(new JFRLogEvent.Error(), msg, t);
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    @Override
    public boolean isErrorEnabled() {
        return false;
    }
    
    // like a filter just backwards: each layer logs more levels
    // unless the event is turned off or no recording is active (which turns all events off)
    static class Error extends JFRLogger {
        
        private static final EventType EVENT_TYPE = EventType.getEventType(JFRLogEvent.Error.class);

        Error() { super(); }
        Error(String name) { super(name); }
        
        @Override
        public final boolean isErrorEnabled() {
            return EVENT_TYPE.isEnabled();
        }
    }
    
    static class Warn extends Error {
        
        private static final EventType EVENT_TYPE = EventType.getEventType(JFRLogEvent.Warn.class);

        Warn() { super(); }
        Warn(String name) { super(name); }
        
        @Override
        public final boolean isWarnEnabled() {
            return EVENT_TYPE.isEnabled();
        }
    }
    
    static class Info extends Warn {
        
        private static final EventType EVENT_TYPE = EventType.getEventType(JFRLogEvent.Info.class);

        Info() { super(); }
        Info(String name) { super(name); }
        
        @Override
        public final boolean isInfoEnabled() {
            return EVENT_TYPE.isEnabled();
        }
    }
    
    static class Debug extends Info {
        
        private static final EventType EVENT_TYPE = EventType.getEventType(JFRLogEvent.Debug.class);

        Debug() { super(); }
        Debug(String name) { super(name); }
        
        @Override
        public final boolean isDebugEnabled() {
            return EVENT_TYPE.isEnabled();
        }
    }
    
    static final class Trace extends Debug {
        
        private static final EventType EVENT_TYPE = EventType.getEventType(JFRLogEvent.Trace.class);

        Trace() { super(); }
        Trace(String name) { super(name); }
        
        @Override
        public final boolean isTraceEnabled() {
            return EVENT_TYPE.isEnabled();
        }
    }
}

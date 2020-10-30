///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVAC_OPTIONS --enable-preview -source 15
//JAVA_OPTIONS --enable-preview -Xmx42m -XX:+UseSerialGC

/*
* MIT License
* This cli tool is part of the JFRLog project.
* https://github.com/mbien/JFRLog
*/
package dev.mbien.jfrlog.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordedThreadGroup;

/**
 * Prints formatted JFR events from JFR dumps or streams them from live repositories.
 * 
 * @author Michael Bien https://mbien.dev
 */
public class JFRPrint {
    
    private static final String VERSION = "0.1";
    
    public static void printUsage() {
        System.out.println("""
            usage: jfrprint <range> <event_name> <message_pattern> <jfr_dump | jfr_repository>

            examples:

            print all events of recording.jfr, this is equivalent to the JDK tool 'jfr print recording.jfr'
             jfrprint "*" "*" recording.jfr

            print all events of recording.jfr using the provided pattern
             jfrprint "*" "*" "{eventName} {startTime} [{remaining}]" recording.jfr

            print all events starting with 'log.' of the last two hours of recording.jfr
             jfrprint 2h "log.*" "{eventName,0d,C} {startTime,dt:yyyy-MM-dd HH:mm:ss.SSS} [{eventThread.javaName}] {origin,0d}: {message} {throwable,o,n}" recording.jfr

            stream all jdk.ThreadStart events from the JFR repository using the provided pattern. Somewhat similar to 'tail -f logfile'
             jfrprint "*" jdk.ThreadStart "{startTime} name: {thread.javaName}, id: {thread.javaThreadId}, group: {thread.group.name}" /path/to/jfr/repository
            """);
        System.out.println("JFRPrint v" + VERSION + " by Michael Bien https://github.com/mbien/JFRLog/");
    }
    
    public static void main(String[] args) throws IOException  {
        
        // for quick tests
//        args = new String[] { 
//            "5h", 
//            "*", 
//            "log.*",
//            "*",               "{eventName} {startTime} [{remaining}]",
//            "log.*",           "{eventName,0d,C} {startTime,dt:yyyy-MM-dd HH:mm:ss.SSS} [{eventThread.javaName}] {origin,0d}: {message} {throwable,o,n}",
//            "jdk.ThreadStart", "{eventName,0d  } {startTime,dt:yyyy-MM-dd HH:mm:ss:SSS} name: {thread.javaName}, id: {thread.javaThreadId}, group: {thread.group.name}",
//            "/tmp/test_dump.jfr"};
        
        if (args.length < 3) {
            printUsage();
            return;
        }
        
        String durString = args[0].toLowerCase();
        String recording = args[args.length-1];
        
        EventFormat[] formats = new EventFormat[(args.length - 1) / 2];
        for (int i = 0; i < formats.length; i++) {
            if(args.length > 3) {
                formats[i] = new EventFormat(args[i*2+1], args[i*2+2]);
            }else{
                formats[i] = new EventFormat(args[i*2+1]);
            }
        }    

        Instant timestamp;
        if(durString.equals("*")) {
            timestamp = null;
        }else{
            if(durString.contains("d")) {
                durString = "p" + durString.replace("d", "dt");
            }else{
                durString = "pt" + durString;
            }
            timestamp = Instant.now().minus(Duration.parse(durString));     
        }
        
        Map<String, EventFormat> eventFormats = new HashMap<>();
        List<EventFormat> eventPrefixFormats = new ArrayList<>();
        
        for (EventFormat format : formats) {
            if(format.nameAsPrefix) {
                eventPrefixFormats.add(format);
            }else{
                eventFormats.put(format.eventName, format);
            }
        }
        Path path = Path.of(recording);
        
        try (EventStream es = Files.isDirectory(path) ? EventStream.openRepository(path) : EventStream.openFile(path)) {
            
            es.onEvent((event) -> {
                
                if(timestamp != null && event.getEndTime().isBefore(timestamp))
                    return;
                
                String eventName = event.getEventType().getName();
                
                EventFormat format = eventFormats.get(eventName);
                if(format == null) {
                    for (EventFormat wildcardFormat : eventPrefixFormats) {
                        if(eventName.startsWith(wildcardFormat.eventName)) {
                            format = wildcardFormat;
                            break;
                        }
                    }
                    if(format == null) {
                        return;
                    }
                }
                
                if(format.format == null) {
                    System.out.println(event.toString());
                }else{
                    System.out.println(formatEvent(event, format));
                }
            });
            
            es.start();
        }
    
    }

    private static String formatEvent(RecordedEvent event, EventFormat format) {
        
        StringBuilder sb = new StringBuilder(256);
        Matcher matcher = format.createMatcher();
        
        int index = 0;
        while(matcher.find()) {
            
            String placeholder = matcher.group(1);
            
            String fieldname;
            int seperator = placeholder.indexOf(',');
            if(seperator != -1) {
                fieldname = placeholder.substring(0, seperator);
            }else{
                fieldname = placeholder;
            }
            
            Object value = null;
            
            if(fieldname.equals("eventName")) { // event name is no field
                value = event.getEventType().getName();
            }else if(event.hasField(fieldname)) {
                value = getCompactValue(event, fieldname, false);
            }else if(fieldname.equals("remaining")) {
                StringJoiner remaining = new StringJoiner(", ");
                Set<String> usedFields = format.placeholders;
                event.getFields().forEach((field) -> {
                    if(!usedFields.contains(field.getName()))
                        remaining.add(field.getName() + ":" + getCompactValue(event, field.getName(), true));
                });
                value = remaining.toString();
            }
            
            matcher.appendReplacement(sb, formatField(value, format.getParams(index++)));
        }
        
        matcher.appendTail(sb);
        
        return sb.toString();
    }

    private static Object getCompactValue(RecordedObject recorded, String fieldname, boolean oneLine) {
        
        Object value = recorded.getValue(fieldname);
        
        if(value instanceof RecordedClass recordedClass) {
            if(oneLine) {
                value = recordedClass.getName();
            }else{
                value = value.toString();
            }
        }else if(value instanceof RecordedClassLoader recordedClassLoader) {
            if(oneLine) {
                value = recordedClassLoader.getName();
            }else{
                value = value.toString();
            }
        }else if(value instanceof RecordedThread recordedThread) {
            if(oneLine) {
                value = recordedThread.getJavaName();
            }else{
                value = value.toString();
            }
        }else if(value instanceof RecordedThreadGroup recordedThreadGroup) {
            if(oneLine) {
                value = recordedThreadGroup.getName();
            }else{
                value = value.toString();
            }
        }else if(value instanceof RecordedStackTrace recordedStackTrace) {
            List<RecordedFrame> frames = recordedStackTrace.getFrames();
            StringJoiner list;
            if(oneLine) {
                list = new StringJoiner(", ", "[", "]");
            }else{
                list = new StringJoiner("\n    ", "    ", "");
            }   
            frames.forEach((frame) -> {
                list.add(frame.getMethod().getType().getName() + "." +frame.getMethod().getName() + "(Line:" + frame.getLineNumber() + ")");
            });
            value = list.toString();
        }else if(value instanceof RecordedObject obj) {
            if(oneLine) {
                StringJoiner list = new StringJoiner(", ", "[", "]");
                obj.getFields().forEach((field) -> {
                    list.add(field.getName() + ":" + getCompactValue(obj, field.getName(), true));
                });
                value = list.toString();
            }else{
                value = value.toString();
            }
        }else if(value instanceof String string) {
            if(oneLine) {
                value = string.replace('\n', ' ');
            }
        }else{
            ValueDescriptor fieldType = getFieldDeep(recorded, fieldname);

            if (fieldType != null && "jdk.jfr.Timestamp".equals(fieldType.getContentType())) {
                value = recorded.getInstant(fieldname); // converts ticks to Instant
            }
        }
        return value;
    }

    // just like event.getValue but for field types. Supports foo.bar paths.
    private static ValueDescriptor getFieldDeep(RecordedObject recorded, String fieldname) {
        int lastDot = fieldname.lastIndexOf('.');
        if(lastDot == -1) {
            return getFieldShallow(recorded, fieldname);
        }else{
            RecordedObject parent = recorded.getValue(fieldname.substring(0, lastDot));
            String name = fieldname.substring(lastDot+1, fieldname.length());
            return getFieldShallow(parent, name);
        }
    }

    private static ValueDescriptor getFieldShallow(RecordedObject recorded, String name) {
        for (ValueDescriptor field : recorded.getFields()) 
            if (field.getName().equals(name)) 
                return field;
        return null;
    }

    private static String formatField(Object value, Param[] parameters) {
        
        if(value == null)
            return containsOptional(parameters) ? "" : "N/A";
        
        for (Param param : parameters)
            value = param.format(value);
        
        return value.toString().replace("$", "\\$"); // matchers hate dollars
    }

    private static boolean containsOptional(Param[] parameters) {
        for (Param parameter : parameters)
            if(parameter instanceof Param.Optional) 
                return true;
        return false;
    }
      
    private final static class EventFormat {
        
        private final static Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        
        private final String format;
        private final String eventName;
        private final boolean nameAsPrefix;
        
        private final Set<String> placeholders;
        private final List<Param[]> parameters;
        
        private EventFormat(String eventName) {
            this(eventName, null);
        }

        private EventFormat(String eventName, String format) {
            
            if(eventName.endsWith("*")) {
                this.eventName = eventName.substring(0, eventName.length()-1);
                this.nameAsPrefix = true;
            }else{
                this.eventName = eventName;
                this.nameAsPrefix = false;
            }
            
            this.format = format;
            
            if(format == null) {
                this.placeholders = Collections.emptySet();
                this.parameters = Collections.emptyList();
            }else{
                Set<String> set = new HashSet<>();
                List<Param[]> list = new ArrayList<>();

                this.pattern.matcher(format).results().forEach((m) -> {

                    String[] parts = m.group(1).split(",");
                    String name = parts[0].split("\\.")[0];

                    Param[] params = new Param[parts.length-1];
                    for (int i = 0; i < params.length; i++) {
                        params[i] = Param.parse(parts[i+1].trim());
                    }
                    list.add(params);
                    set.add(name);
                });
                this.placeholders = Collections.unmodifiableSet(set);
                this.parameters = Collections.unmodifiableList(list);
            }
        }
        
        private Matcher createMatcher() {
            return pattern.matcher(format);
        }
        
        private Param[] getParams(int placeholderIndex) {
            return parameters.get(placeholderIndex);
        }
    }
                
    private static abstract /*sealed*/ class Param {

        public abstract String format(Object value);
        
        private final static class NewLine extends Param {
            @Override public String format(Object value) { return "\n" + value.toString(); }
        }
        
        private final static class LowerCase extends Param {
            @Override public String format(Object value) { return value.toString().toLowerCase(); }
        }
        
        private final static class UpperCase extends Param {
            @Override public String format(Object value) { return value.toString().toUpperCase(); }
        }
        
        private final static class Optional extends Param {
            @Override public String format(Object value) { return value == null ? "" : value.toString(); }
        }

        private final static class InstantPattern extends Param {
            private final DateTimeFormatter datetime;
            public InstantPattern(DateTimeFormatter datetime) { this.datetime = datetime; }
            
            @Override public String format(Object value) { return datetime.format((TemporalAccessor)value); }
        }
        
        private final static class NDots extends Param {
            private final int n;
            private NDots(int n) { this.n = n; }

            @Override
            public String format(Object value) {
                String[] parts = value.toString().split("\\.");

                StringJoiner joiner = new StringJoiner(".");
                int dots = Math.min(n, parts.length-1);
                for (int i = parts.length-dots-1; i < parts.length; i++) {
                    joiner.add(parts[i]);
                }
                return joiner.toString();
            }
        }
        
        private static Param parse(String str) {
            if(str.equals("n")) {        return new Param.NewLine();
            }else if(str.equals("o")) {  return new Param.Optional();
            }else if(str.equals("c")) {  return new Param.LowerCase();
            }else if(str.equals("C")) {  return new Param.UpperCase();
            }else if(str.startsWith("dt:")) {
                return new Param.InstantPattern(
                        DateTimeFormatter.ofPattern(str.substring(3))
                                         .withZone(ZoneId.systemDefault()));
            }else if(str.endsWith("d")) {return new Param.NDots(Integer.parseInt(str, 0, str.length()-1, 10)); }
            
            throw new IllegalArgumentException("unknon parameter: '"+str+"'");
        }
    }
}

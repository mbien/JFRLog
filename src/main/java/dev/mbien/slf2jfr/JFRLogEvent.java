package dev.mbien.slf2jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event representing a log message, its origin and a {@link Throwable} if provided.
 * 
 * @author mbien
 */
@Category("JFR Logger")
@StackTrace(false)
class JFRLogEvent extends Event {
    
    @Label("Log Message")
    String message;
    
    @Label("Source of the log message")
    String origin;
    
    @Label("A Throwable printed as String or null")
    String throwable;

    private JFRLogEvent() {}
    
    @Name("log.Trace")
    @Label("Trace log event")
    @Description("Someone logged something.")
    final static class Trace extends JFRLogEvent {}
    
    @Name("log.Debug")
    @Label("Debug log event")
    @Description("Someone logged something.")
    final static class Debug extends JFRLogEvent {}
    
    @Name("log.Info")
    @Label("Info log event")
    @Description("Someone logged something.")
    final static class Info extends JFRLogEvent {}
    
    @Name("log.Warn")
    @Label("Warning log event")
    @Description("Someone logged something.")
    final static class Warn extends JFRLogEvent {}
    
    @Name("log.Error")
    @Label("Error log event")
    @Description("Someone logged something.")
    final static class Error extends JFRLogEvent {}
}
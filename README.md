# JFRLog - a SLF4J to JFR Bridge
This project implements a SLF4J logger which records all log messages as Java Flight Recorder events.


## quickstart
1. replace your SLF4J compatible logging impl with slf4j-jfr-bridge-x.x.x.jar
2. start the JVM with JFR enabled (e.g. -XX:StartFlightRecording=filename=logs/recording.jfr,dumponexit=true)
   or enable recording later
3. check the flight recorder repository or recording dump for log.* events:

```
$ jfr print --events "log.*" logs/recording.jfr
...
log.Info {
  startTime = 07:38:02.730
  message = "Started @5532ms"
  origin = "org.eclipse.jetty.server.Server"
  throwable = N/A
  eventThread = "main" (javaThreadId = 1)
}
...
```
note: JFRLog has currently no fallback, if no recording is active you won't see any logs.

## maven central coordinates
```xml
    <!-- depends on slf4j-api already, but feel free to add it anyway -->
    <dependency>
        <groupId>dev.mbien.jfrlog</groupId>
        <artifactId>slf4j-jfr-bridge</artifactId>
        <version>0.1.0</version>
    </dependency>
```

## configuration
JFRLog can be configured in two ways: via JVM -D arguments or via a jfrlog.properties
file in the classpath. JVM -D properties are handled with higher priority and override
properties with the same key stored in the file.
(note: disabling specific log events in the JFR recording profile will filter the loggers too)

example:
```
java -Djfrlog.default=info -Djfrlog.dev.cool.app=error -Djfrlog.dev.cool.app.MyKlass=debug (...) app.jar
```
Sets MyKlass to debug, the rest of the package to error and the default log level
to info for everything else. The most specific rule wins (order does not matter).


other options:
```
jfrlog.recordOrigin=true
```
Records the origin (logger name) of the log message. Enabled by default.

```
jfrlog.loggerCache=false
```
This is off by default and should be kept off unless there is a good reason to enable
it. It is going to guarantee that the same logger instance is returned for a given
logger name.


## requirements
Requires Java 8+ to run, but Java 14+ to build/test since the junit tests require the JFR
streaming API (JEP 349).

## license
This project is distributed under the MIT License, see LICENSE file.

package org.slf4j.impl;

import dev.mbien.slf2jfr.JFRLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;


/**
 * Main SLF4J entry point.
 * 
 * @author mbien
 */
public class StaticLoggerBinder implements LoggerFactoryBinder {

    private static final StaticLoggerBinder instance = new StaticLoggerBinder();

    public static String REQUESTED_API_VERSION = "1.6.99";

    private StaticLoggerBinder() { }
    
    // required by SLF4J
    public static final StaticLoggerBinder getSingleton() {
        return instance;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return JFRLoggerFactory.getFactory();
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return JFRLoggerFactory.class.getName();
    }

}

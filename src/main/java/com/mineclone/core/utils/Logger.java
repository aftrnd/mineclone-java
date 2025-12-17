package com.mineclone.core.utils;

/**
 * Centralized logging system with log levels.
 * Helps prevent console spam and makes debugging easier.
 */
public class Logger {
    public enum Level {
        ERROR(0, "❌ ERROR"),
        WARN(1, "⚠️  WARN"),
        INFO(2, "ℹ️  INFO"),
        DEBUG(3, "🔍 DEBUG"),
        TRACE(4, "📝 TRACE");
        
        final int priority;
        final String prefix;
        
        Level(int priority, String prefix) {
            this.priority = priority;
            this.prefix = prefix;
        }
    }
    
    private static Level currentLevel = Level.INFO;
    private static boolean showTimestamps = false;
    
    public static void setLevel(Level level) {
        currentLevel = level;
        info("Logger", "Log level set to: " + level);
    }
    
    public static void setShowTimestamps(boolean show) {
        showTimestamps = show;
    }
    
    private static void log(Level level, String category, String message) {
        if (level.priority <= currentLevel.priority) {
            String timestamp = showTimestamps ? 
                String.format("[%d] ", System.currentTimeMillis()) : "";
            System.out.println(timestamp + level.prefix + " [" + category + "] " + message);
        }
    }
    
    public static void error(String category, String message) {
        log(Level.ERROR, category, message);
    }
    
    public static void error(String category, String message, Throwable throwable) {
        error(category, message);
        if (currentLevel.priority >= Level.ERROR.priority) {
            throwable.printStackTrace();
        }
    }
    
    public static void warn(String category, String message) {
        log(Level.WARN, category, message);
    }
    
    public static void info(String category, String message) {
        log(Level.INFO, category, message);
    }
    
    public static void debug(String category, String message) {
        log(Level.DEBUG, category, message);
    }
    
    public static void trace(String category, String message) {
        log(Level.TRACE, category, message);
    }
    
    public static boolean isDebugEnabled() {
        return currentLevel.priority >= Level.DEBUG.priority;
    }
    
    public static boolean isTraceEnabled() {
        return currentLevel.priority >= Level.TRACE.priority;
    }
}


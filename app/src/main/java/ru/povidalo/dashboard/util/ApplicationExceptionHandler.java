package ru.povidalo.dashboard.util;

public class ApplicationExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler previousHandler;
    
    public ApplicationExceptionHandler() {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (handler instanceof ApplicationExceptionHandler) {
            previousHandler = ((ApplicationExceptionHandler) handler).previousHandler;
        } else {
            previousHandler = handler;
        }
    }
    
    public ApplicationExceptionHandler(Thread thread) {
        if (thread != null) {
            Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
            if (handler instanceof ApplicationExceptionHandler) {
                previousHandler = ((ApplicationExceptionHandler) handler).previousHandler;
            } else {
                previousHandler = handler;
            }
        } else {
            previousHandler = null;
        }
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Utils.logError("Uncaught exception ", ex);
        
        if (previousHandler != null && thread != null) {
            previousHandler.uncaughtException(thread, ex);
        }
    }
}

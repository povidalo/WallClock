package ru.povidalo.dashboard.command;

import android.support.annotation.NonNull;
import android.util.LongSparseArray;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import ru.povidalo.dashboard.util.ApplicationExceptionHandler;

/**
 * Created by povidalo on 29.06.18.
 */

public class CommandController {
    private final static int REQUEST_THREAD_POOL_SIZE = 6;
    
    private static final Object instanceLockObject = new Object();
    private static final Object queueLockObject = new Object();
    private static CommandController instance = null;
    
    private ScheduledExecutorService scheduleExecutor = null;
    private LongSparseArray<Future> runningTasks = new LongSparseArray<>();
    private Set<Command> delayedCommands = new HashSet<Command>();
    
    private static CommandController getInstance() {
        if (instance == null) {
            synchronized (instanceLockObject) {
                if (instance == null) {
                    instance = new CommandController();
                }
            }
        }
        
        return instance;
    }
    
    private CommandController() {
        scheduleExecutor = new Executor(REQUEST_THREAD_POOL_SIZE);
    }
    
    public static void execute(Command command) {
        if (command != null) {
            command.start();
            getInstance().runningTasks.put(command.getId(), getInstance().scheduleExecutor.submit(command));
        }
    }
    
    public static void executeDelayed(final Command command, long delay) {
        if (command != null) {
            if (delay > 0) {
                synchronized (queueLockObject) {
                    getInstance().delayedCommands.add(command);
                }
                getInstance().scheduleExecutor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (queueLockObject) {
                            if (getInstance().delayedCommands.contains(command)) {
                                getInstance().delayedCommands.remove(command);
                            } else {
                                return;
                            }
                        }
                        execute(command);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            } else {
                execute(command);
            }
        }
    }
    
    public static boolean hasScheduled(Class<? extends Command> clazz) {
        if (clazz != null) {
            synchronized (queueLockObject) {
                for (Iterator<Command> i = getInstance().delayedCommands.iterator(); i.hasNext(); ) {
                    Command cmd = i.next();
                    if (cmd.getClass().equals(clazz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public static void cancelAllDelayedCommandsOfType(Class<? extends Command> clazz) {
        if (clazz != null) {
            synchronized (queueLockObject) {
                for (Iterator<Command> i = getInstance().delayedCommands.iterator(); i.hasNext(); ) {
                    Command cmd = i.next();
                    if (cmd.getClass().equals(clazz)) {
                        cmd.breakExecution();
                        i.remove();
                    }
                }
            }
        }
    }
    
    public static void cancelDelayedCommand(long id) {
        if (id > 0) {
            synchronized (queueLockObject) {
                for (Iterator<Command> i = getInstance().delayedCommands.iterator(); i.hasNext(); ) {
                    Command cmd = i.next();
                    if (cmd.getId() == id) {
                        cmd.breakExecution();
                        i.remove();
                        break;
                    }
                }
            }
        }
    }
    
    public static void cancelRunningCommand(long id) {
        if (id > 0) {
            synchronized (queueLockObject) {
                Future task = getInstance().runningTasks.get(id);
                if (task != null && !task.isDone()) {
                    getInstance().runningTasks.remove(id);
                    task.cancel(true);
                }
            }
        }
    }
    
    private static class Executor extends ScheduledThreadPoolExecutor {
        ApplicationExceptionHandler exceptionHandler = new ApplicationExceptionHandler(null);
        
        public Executor(int poolSize) {
            super(poolSize, new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable r) {
                    final Thread t = new Thread(r);
                    t.setName(r.getClass().getSimpleName());
                    t.setUncaughtExceptionHandler(new ApplicationExceptionHandler(t));
                    return t;
                }
            });
        }
        
        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            
            if (r instanceof FutureTask) {
                try {
                    ((FutureTask) r).get();
                } catch (Exception e) {
                    t = e;
                }
            }
            
            if (t != null && (t instanceof ExecutionException)) {
                //exceptionHandler.uncaughtException(null, t);  // do not fail, just report
                throw new RuntimeException(t); // fail everything on command error
            }
        }
    }
}


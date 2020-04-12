package ru.povidalo.dashboard.command;

import android.os.SystemClock;

import okhttp3.Response;
import ru.povidalo.dashboard.Dashboard;

/**
 * Created by povidalo on 29.06.18.
 */

public abstract class Command implements Runnable {
    protected static final Object lockObject = new Object();

    protected static long currentId = SystemClock.uptimeMillis();

    protected boolean executionRequested = false;
    protected boolean autoFinish = true;

    protected volatile boolean breakExecution = false;

    protected ICommand listener = null;
    protected final long id;

    private ICommand.CommandState currentState = null;

    public Command(ICommand listener) {
        this.listener = listener;
        synchronized (lockObject) {
            id = currentId;
            currentId++;
        }
    }

    public void setListener(ICommand listener) {
        this.listener = listener;
    }

    public long getId() {
        return id;
    }

    public void start() {
        breakExecution |= started();
    }

    @Override
    public void run() {
        if(!breakExecution) {
            doInBackground();
        }
        if (autoFinish) {
            breakExecution |= finished();
        }
    }

    public void execute() {
        if (!executionRequested) {
            executionRequested = true;
            CommandController.execute(this);
        } else {
            throw new IllegalStateException("Command execution already requested");
        }
    }

    public void executeDelayed(long delay) {
        if (!executionRequested) {
            executionRequested = true;
            CommandController.executeDelayed(this, delay);
        } else {
            throw new IllegalStateException("Command execution already requested");
        }
    }

    protected abstract void doInBackground();

    public void breakExecution() {
        breakExecution = true;
        listener = null;
    }

    public boolean isExecutionTerminated() {
        return breakExecution;
    }

    protected boolean started() {
        currentState = ICommand.CommandState.STARTED;
        return notifyStatusChanged();
    }

    protected boolean finished() {
        currentState = ICommand.CommandState.FINISHED;
        return notifyStatusChanged();
    }

    protected boolean success() {
        currentState = ICommand.CommandState.SUCCESS;
        return notifyStatusChanged();
    }

    protected boolean failed() {
        currentState = ICommand.CommandState.FAILED;
        return notifyStatusChanged();
    }
    
    protected void setAutoFinish(boolean autoFinish) {
        this.autoFinish = autoFinish;
    }

    private boolean notifyStatusChanged() {
        if (listener != null) {
            return listener.onCommandState(this, currentState);
        }
        return false;
    }

    boolean processError(Response response, ProtocolError error) {
        if(response == null || error.httpCode != 200) {
            error.type = isNetworkError(error.httpCode) ? ProtocolError.Type.NETWORK_ERROR : ProtocolError.Type.SERVER_ERROR;
            if(listener != null) {
                listener.onCommandError(this, error);
            }
            failed();
            return true;
        }
        return false;
    }

    private boolean isNetworkError(int code) {
        return !Dashboard.isOnline() ||
                code == 524 ||
                code == 522 ||
                code == 504;
    }

    public ICommand.CommandState getCurrentState() {
        return currentState;
    }
}

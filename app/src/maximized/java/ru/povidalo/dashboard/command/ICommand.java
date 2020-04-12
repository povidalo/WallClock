package ru.povidalo.dashboard.command;

/**
 * Created by povidalo on 29.06.18.
 */

public interface ICommand {
    public static enum CommandState {
        STARTED,
        NETWORK_REQUEST_SENT,
        NETWORK_RESPONSE_RECEIVED,
        NETWORK_RESPONSE_DECOMPRESSED,
        NETWORK_RESPONSE_PARSED,
        SUCCESS,
        FAILED,
        FINISHED
    }

    public boolean onCommandProgress(Command command, int progress);
    public boolean onCommandState(Command command, CommandState state);
    public boolean onCommandError(Command command, ProtocolError error);
}
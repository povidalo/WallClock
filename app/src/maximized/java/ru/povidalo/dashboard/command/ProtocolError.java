package ru.povidalo.dashboard.command;

/**
 * Created by povidalo on 29.06.18.
 */

public class ProtocolError {
    public enum Type {
        NETWORK_ERROR, DATA_ERROR, SERVER_ERROR;
    }
    public int httpCode = 0;
    public String httpMessage = null;
    public String protocolMessage = null;
    public Type type = null;
}

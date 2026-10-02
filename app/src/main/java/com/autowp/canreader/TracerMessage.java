package com.autowp.canreader;

import com.autowp.can.CanMessage;

import java.util.Date;

/**
 * Represents a single traced CAN message with timestamp.
 */
public class TracerMessage {
    private final CanMessage canMessage;
    private final Date timestamp;

    public TracerMessage(CanMessage canMessage, Date timestamp) {
        this.canMessage = canMessage;
        this.timestamp = timestamp;
    }

    public CanMessage getCanMessage() {
        return canMessage;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}

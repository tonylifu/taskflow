package com.taskflow.service;

/**
 * Thrown by {@link TaskService} when the Tasks API rejects a request or is unreachable.
 * Backing beans translate this into PrimeFaces growl messages,
 * mirroring the toast notifications shown in the React app.
 */
public class TaskApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskApiException(String message) {
        super(message);
    }

    public TaskApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

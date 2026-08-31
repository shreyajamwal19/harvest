package com.harvest.exception;

/** Thrown when a change-password request's currentPassword doesn't match what's on file. */
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException(String message) {
        super(message);
    }
}

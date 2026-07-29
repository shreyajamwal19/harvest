package com.harvest.chef.exception;

/** Thrown when the Chef Brain's underlying reasoning call fails or returns something unusable. */
public class ChefReasoningException extends RuntimeException {

    public ChefReasoningException(String message) {
        super(message);
    }

    public ChefReasoningException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.harvest.exception;

/** Thrown when LoginAttemptService has locked out further login attempts for an email. */
public class AccountTemporarilyLockedException extends RuntimeException {
    public AccountTemporarilyLockedException(String message) {
        super(message);
    }
}

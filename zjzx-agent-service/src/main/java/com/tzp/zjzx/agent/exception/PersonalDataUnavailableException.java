package com.tzp.zjzx.agent.exception;

public class PersonalDataUnavailableException extends RuntimeException {

    public PersonalDataUnavailableException(String message) {
        super(message);
    }

    public PersonalDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

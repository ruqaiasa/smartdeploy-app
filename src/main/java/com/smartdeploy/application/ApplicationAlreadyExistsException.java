package com.smartdeploy.application;

public class ApplicationAlreadyExistsException
        extends RuntimeException {

    public ApplicationAlreadyExistsException(
            String message
    ) {
        super(message);
    }
}
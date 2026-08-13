package com.smartdeploy.integration.jenkins;

public class JenkinsIntegrationException
        extends RuntimeException {

    public JenkinsIntegrationException(
            String message
    ) {
        super(message);
    }

    public JenkinsIntegrationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
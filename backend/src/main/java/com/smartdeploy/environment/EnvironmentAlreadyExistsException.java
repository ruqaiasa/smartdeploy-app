package com.smartdeploy.environment;

public class EnvironmentAlreadyExistsException extends RuntimeException {

    public EnvironmentAlreadyExistsException(
            String applicationName,
            EnvironmentType type
    ) {
        super(
                "L'application '" + applicationName
                        + "' possède déjà un environnement de type "
                        + type
        );
    }
}
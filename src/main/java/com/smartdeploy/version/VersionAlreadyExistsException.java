package com.smartdeploy.version;

public class VersionAlreadyExistsException extends RuntimeException {

    public VersionAlreadyExistsException(
            String applicationName,
            String version
    ) {
        super(
                "L'application '" + applicationName
                        + "' possède déjà la version "
                        + version
        );
    }
}
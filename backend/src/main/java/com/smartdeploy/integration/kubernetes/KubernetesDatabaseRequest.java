package com.smartdeploy.integration.kubernetes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record KubernetesDatabaseRequest(

        @NotNull
        DatabaseMode mode,

        @NotBlank
        String databaseName,

        @NotBlank
        String username,

        @NotBlank
        String passwordSecretName,

        @NotBlank
        String passwordSecretKey,

        String externalHost,

        @Min(1)
        @Max(65535)
        int port,

        @Min(1)
        int storageGi

) {
    public enum DatabaseMode {
        EXTERNAL,
        MANAGED
    }
}
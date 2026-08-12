package com.smartdeploy.application;

import com.smartdeploy.environment.EnvironmentType;

public record ApplicationEnvironmentResponse(

        Long id,

        String name,

        EnvironmentType type,

        String namespace,

        String clusterUrl,

        boolean active

) {
}
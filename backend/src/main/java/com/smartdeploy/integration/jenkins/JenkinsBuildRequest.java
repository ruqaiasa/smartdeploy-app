package com.smartdeploy.integration.jenkins;

public record JenkinsBuildRequest(

        Long deploymentId,

        Long versionId,

        String applicationName,

        String version,

        String gitBranch,

        String dockerImage,

        String environment,

        String namespace

) {
}
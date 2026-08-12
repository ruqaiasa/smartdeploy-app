package com.smartdeploy.integration.jenkins;

public record JenkinsBuildResponse(

        String queueUrl,

        String message

) {
}
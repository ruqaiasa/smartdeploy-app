package com.smartdeploy.version;

import jakarta.validation.constraints.Size;

public record UpdateBuildMetadataRequest(

        Integer buildNumber,

        @Size(max = 500)
        String dockerImage,

        @Size(max = 500)
        String artifactPath,

        @Size(max = 1000)
        String buildLogsUrl

) {
}
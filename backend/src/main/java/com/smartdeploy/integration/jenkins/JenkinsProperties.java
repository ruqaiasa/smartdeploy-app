package com.smartdeploy.integration.jenkins;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartdeploy.jenkins")
public record JenkinsProperties(

        String url,

        String username,

        String token,

        String jobName

) {
}
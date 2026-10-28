package com.smartdeploy;

import com.smartdeploy.integration.jenkins.JenkinsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(JenkinsProperties.class)
@EnableScheduling
public class SmartdeployBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				SmartdeployBackendApplication.class,
				args
		);
	}
}
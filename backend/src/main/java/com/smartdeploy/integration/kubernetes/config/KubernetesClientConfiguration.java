package com.smartdeploy.integration.kubernetes.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class KubernetesClientConfiguration {

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() throws Exception {

        String masterUrl = "https://192.168.85.129:6443";

        Path tokenPath = Path.of(
                "C:/smartdeploy-k8s/smartdeploy-token"
        );

        Path caPath = Path.of(
                "C:/smartdeploy-k8s/smartdeploy-ca.crt"
        );

        String token = Files.readString(
                tokenPath,
                StandardCharsets.UTF_8
        ).trim();

        if (token.isBlank()) {
            throw new IllegalStateException(
                    "Kubernetes ServiceAccount token is empty"
            );
        }

        Config config = new ConfigBuilder()
                .withMasterUrl(masterUrl)
                .withOauthToken(token)
                .withCaCertFile(caPath.toString())
                .withTrustCerts(false)
                .build();

        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }
}
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

    /*
     * Adresse du cluster et fichiers d'accès du compte de service, lus dans application.yml
     * (section smartdeploy.kubernetes), et non plus écrits en dur dans le code.
     */
    @org.springframework.beans.factory.annotation.Value("${smartdeploy.kubernetes.master-url:https://192.168.85.129:6443}")
    private String masterUrl;

    @org.springframework.beans.factory.annotation.Value("${smartdeploy.kubernetes.token-path:C:/smartdeploy-k8s/smartdeploy-token}")
    private String tokenFile;

    @org.springframework.beans.factory.annotation.Value("${smartdeploy.kubernetes.ca-path:C:/smartdeploy-k8s/smartdeploy-ca.crt}")
    private String caFile;

    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() throws Exception {
        Path tokenPath = Path.of(tokenFile);
        Path caPath = Path.of(caFile);

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
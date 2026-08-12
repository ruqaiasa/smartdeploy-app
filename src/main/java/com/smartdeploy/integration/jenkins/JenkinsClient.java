package com.smartdeploy.integration.jenkins;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JenkinsClient {

    private final JenkinsProperties properties;
    private final RestClient restClient;

    public JenkinsClient(
            JenkinsProperties properties
    ) {
        this.properties = properties;

        this.restClient = RestClient.builder()
                .baseUrl(
                        removeTrailingSlash(
                                properties.url()
                        )
                )
                .defaultHeaders(headers ->
                        headers.setBasicAuth(
                                properties.username(),
                                properties.token(),
                                StandardCharsets.UTF_8
                        )
                )
                .build();
    }

    public String triggerBuild(
            Map<String, String> parameters
    ) {
        MultiValueMap<String, String> formData =
                new LinkedMultiValueMap<>();

        parameters.forEach((key, value) -> {
            if (key != null && value != null) {
                formData.add(key, value);
            }
        });

        return restClient.post()
                .uri(
                        "/job/{jobName}/buildWithParameters",
                        properties.jobName()
                )
                .contentType(
                        MediaType.APPLICATION_FORM_URLENCODED
                )
                .body(formData)
                .exchange((request, response) -> {

                    if (!response.getStatusCode()
                            .is2xxSuccessful()) {

                        throw new JenkinsIntegrationException(
                                "Jenkins a refusé le déclenchement du job. "
                                        + "Statut HTTP : "
                                        + response.getStatusCode()
                        );
                    }

                    String queueUrl = response
                            .getHeaders()
                            .getFirst(HttpHeaders.LOCATION);

                    if (queueUrl == null
                            || queueUrl.isBlank()) {

                        throw new JenkinsIntegrationException(
                                "Jenkins n'a retourné aucune URL "
                                        + "de file d'attente"
                        );
                    }

                    return queueUrl;
                });
    }

    private String removeTrailingSlash(
            String url
    ) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "L'URL Jenkins n'est pas configurée"
            );
        }

        String normalizedUrl = url.trim();

        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(
                    0,
                    normalizedUrl.length() - 1
            );
        }

        return normalizedUrl;
    }
}
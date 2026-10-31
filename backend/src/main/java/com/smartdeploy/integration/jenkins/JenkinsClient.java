package com.smartdeploy.integration.jenkins;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
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
                // Délais bornés : un Jenkins lent ne doit jamais bloquer
                // une requête SmartDeploy indéfiniment.
                .requestFactory(createRequestFactory())
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

    /**
     * Arrête un build en cours. Renvoie false si Jenkins ne connaît pas
     * ce build (déjà terminé et purgé, numéro erroné).
     */
    public boolean stopBuild(int buildNumber) {
        return restClient.post()
                .uri(
                        "/job/{jobName}/{buildNumber}/stop",
                        properties.jobName(),
                        buildNumber
                )
                .exchange((request, response) ->
                        acceptedOrNotFound(response.getStatusCode(), "l'arrêt du build #" + buildNumber)
                );
    }

    /**
     * Retire un élément de la file d'attente. Renvoie false s'il n'existe
     * plus (déjà démarré ou expiré).
     */
    public boolean cancelQueueItem(long queueId) {
        return restClient.post()
                .uri("/queue/cancelItem?id={id}", queueId)
                .exchange((request, response) ->
                        acceptedOrNotFound(response.getStatusCode(), "l'annulation de la file d'attente")
                );
    }

    /**
     * JSON de l'élément de file d'attente, ou null s'il n'existe plus.
     * Quand le build a démarré, il contient executable.number.
     */
    public String getQueueItemJson(long queueId) {
        return restClient.get()
                .uri("/queue/item/{id}/api/json", queueId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new JenkinsIntegrationException(
                                "Jenkins a refusé la lecture de la file d'attente. Statut HTTP : "
                                        + response.getStatusCode()
                        );
                    }
                    return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });
    }

    private static boolean acceptedOrNotFound(HttpStatusCode status, String action) {
        // Jenkins répond 200/204, ou 302 vers la page du job.
        if (status.is2xxSuccessful() || status.is3xxRedirection()) {
            return true;
        }
        if (status.value() == 404) {
            return false;
        }
        throw new JenkinsIntegrationException(
                "Jenkins a refusé " + action + ". Statut HTTP : " + status
        );
    }

    private static JdkClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
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
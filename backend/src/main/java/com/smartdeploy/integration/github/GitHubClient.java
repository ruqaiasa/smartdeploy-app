package com.smartdeploy.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture de l'API publique de GitHub (sans jeton) : le commit le plus récent d'une branche.
 * Délais courts ; toute erreur donne « inconnu », jamais une exception : c'est une aide, pas une condition.
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    static final Pattern REPOSITORY = Pattern.compile("github\\.com[/:]([^/]+)/([^/]+?)(?:\\.git)?/?$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient rest;

    public GitHubClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.rest = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "SmartDeploy")
                .build();
    }

    /** Le dernier commit de la branche, ou vide si le dépôt n'est pas sur GitHub ou injoignable. */
    public Optional<String> headCommit(String repositoryUrl, String branch) {
        if (repositoryUrl == null || branch == null || branch.isBlank()) {
            return Optional.empty();
        }
        Matcher m = REPOSITORY.matcher(repositoryUrl.trim());
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            String body = rest.get()
                    .uri(uri -> uri.path("/repos/{owner}/{repo}/commits")
                            .queryParam("sha", branch.trim())
                            .queryParam("per_page", 1)
                            .build(m.group(1), m.group(2)))
                    .retrieve()
                    .body(String.class);
            JsonNode commits = MAPPER.readTree(body);
            if (commits.isArray() && !commits.isEmpty()) {
                return Optional.ofNullable(commits.get(0).path("sha").asText(null));
            }
        } catch (Exception exception) {
            log.debug("GitHub : dernier commit de {} indisponible ({})", branch, exception.getMessage());
        }
        return Optional.empty();
    }
}

package com.smartdeploy.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit un dépôt public GitHub : la liste de ses fichiers (une seule requête à l'API), puis le contenu
 * des seuls fichiers utiles à l'analyse (pom.xml, Dockerfile, configuration, package.json).
 */
@Component
public class RepositoryInspector {

    static final Pattern REPOSITORY = Pattern.compile("github\\.com[/:]([^/\\s]+)/([^/\\s]+?)(?:\\.git)?/?$");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final List<String> USEFUL = List.of("pom.xml", "backend/pom.xml", "Dockerfile", "backend/Dockerfile",
            "frontend/Dockerfile", "src/main/resources/application.properties", "src/main/resources/application.yml",
            "src/main/resources/application.yaml", "backend/src/main/resources/application.properties",
            "backend/src/main/resources/application.yml", "backend/src/main/resources/application.yaml");

    private final RestClient api;
    private final RestClient raw;

    public RepositoryInspector() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.api = RestClient.builder().requestFactory(factory).baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json").defaultHeader("User-Agent", "SmartDeploy").build();
        this.raw = RestClient.builder().requestFactory(factory).baseUrl("https://raw.githubusercontent.com")
                .defaultHeader("User-Agent", "SmartDeploy").build();
    }

    public RepositorySnapshot inspect(String repositoryUrl, String branch) {
        Matcher m = REPOSITORY.matcher(repositoryUrl == null ? "" : repositoryUrl.trim());
        if (!m.find()) {
            throw new IllegalArgumentException("Adresse GitHub attendue, par exemple https://github.com/equipe/projet");
        }
        String owner = m.group(1), repo = m.group(2), ref = branch == null || branch.isBlank() ? "main" : branch.trim();
        JsonNode tree;
        try {
            String body = api.get().uri("/repos/{o}/{r}/git/trees/{b}?recursive=1", owner, repo, ref).retrieve().body(String.class);
            tree = MAPPER.readTree(body).path("tree");
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Dépôt ou branche « " + ref + " » introuvable (le dépôt doit être public)");
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException("GitHub limite le nombre de vérifications par heure : réessayez dans quelques minutes");
        } catch (Exception e) {
            throw new IllegalStateException("GitHub est injoignable : " + e.getMessage());
        }
        Set<String> paths = new HashSet<>();
        tree.forEach(n -> {
            if ("blob".equals(n.path("type").asText())) paths.add(n.path("path").asText());
        });
        Map<String, String> files = new HashMap<>();
        for (String path : USEFUL) {
            if (paths.contains(path)) files.put(path, fetch(owner, repo, ref, path));
        }
        if (paths.contains("frontend/package.json")) files.put("frontend/package.json", fetch(owner, repo, ref, "frontend/package.json"));
        return new RepositorySnapshot(owner + "/" + repo, ref, paths, files);
    }

    private String fetch(String owner, String repo, String ref, String path) {
        try {
            String body = raw.get().uri("/{o}/{r}/{b}/" + path, owner, repo, ref).retrieve().body(String.class);
            return body == null ? "" : body;
        } catch (Exception e) {
            return "";
        }
    }
}

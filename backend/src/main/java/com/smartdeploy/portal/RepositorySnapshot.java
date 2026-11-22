package com.smartdeploy.portal;

import java.util.Map;
import java.util.Set;

/** Ce que l'on sait d'un dépôt : la liste de ses fichiers, et le contenu de quelques-uns. */
public record RepositorySnapshot(String repository, String branch, Set<String> paths, Map<String, String> files) {

    public boolean has(String path) {
        return paths.contains(path);
    }

    public String file(String path) {
        return files.getOrDefault(path, "");
    }
}

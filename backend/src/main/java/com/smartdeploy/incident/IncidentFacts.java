package com.smartdeploy.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction des faits précis d'un incident, à partir des preuves déjà rassemblées :
 * le fichier et la ligne d'une erreur de compilation, le test en échec, le code de
 * sortie d'un conteneur et sa durée de fonctionnement, le port sondé, la bibliothèque
 * vulnérable et sa version corrigée… L'action proposée reprend ces valeurs exactes.
 * Classe pure, sans réseau : chaque fait provient d'une ligne de preuve, rien n'est supposé.
 */
public final class IncidentFacts {

    /** Un fait établi : un libellé et sa valeur, tirée d'une preuve. */
    public record Fact(String label, String value) {
    }

    /**
     * @param facts        faits établis
     * @param decisiveLine la ligne de log qui explique le mieux l'incident (ou null)
     * @param action       action concrète avec les vraies valeurs (null : garder l'action de la signature)
     */
    public record Enrichment(List<Fact> facts, String decisiveLine, String action) {
    }

    static final Map<Integer, String> EXIT_CODES = Map.of(
            0, "arrêt normal du programme",
            1, "erreur de l'application",
            2, "mauvaise utilisation d'une commande",
            126, "commande non exécutable",
            127, "commande introuvable",
            137, "tué par le système (mémoire épuisée ou arrêt forcé)",
            139, "erreur mémoire du programme (accès invalide)",
            143, "arrêté proprement par Kubernetes (SIGTERM)");

    private static final Pattern EXIT_CODE = Pattern.compile("dernier arrêt : (\\w+) \\(exit code (\\d+)\\)");
    private static final Pattern UPTIME = Pattern.compile("durée de fonctionnement : (\\d+) s");
    private static final Pattern CAUSED_BY = Pattern.compile("Caused by: ([\\w.$]+): (.+)");
    private static final Pattern APP_ERROR = Pattern.compile("(\\bERROR\\b|Exception|FATAL|Panne simulée)");
    private static final Pattern JAVAC = Pattern.compile("\\[ERROR\\] (?:\\S*/)?([\\w$-]+\\.java):\\[(\\d+),(\\d+)\\] (.+)");
    private static final Pattern SUREFIRE = Pattern.compile("\\[ERROR\\]\\s+([A-Z][\\w$]*)\\.([\\w$]+):(\\d+)\\s+(.+)");
    private static final Pattern TSC = Pattern.compile("([\\w./-]+\\.(?:ts|tsx|vue))[(:](\\d+)[,:](\\d+)\\)?:?\\s*-?\\s*error (TS\\d+): (.+)");
    private static final Pattern DOCKER_INSTRUCTION = Pattern.compile("unknown instruction: (\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCKER_LINE = Pattern.compile("[Dd]ockerfile(?::| parse error on line )(\\d+)");
    private static final Pattern TRIVY_ROW = Pattern.compile(
            "│\\s*([^│]*?)\\s*│\\s*(CVE-\\d{4}-\\d+)\\s*│\\s*(CRITICAL|HIGH|)\\s*│[^│]*│\\s*([^│]*?)\\s*│\\s*([^│]*?)\\s*│");
    private static final Pattern PLACEHOLDER = Pattern.compile("Could not resolve placeholder '([^']+)'");
    private static final Pattern ENV_ABSENT = Pattern.compile("variable ([A-Z][A-Z0-9_]{2,}) absente|([A-Z][A-Z0-9_]{2,}) (?:is not set|must be set|is required)");
    private static final Pattern SECRET_NOT_FOUND = Pattern.compile("secret \"([^\"]+)\" not found");
    private static final Pattern PROBE_PORT = Pattern.compile("dial tcp [\\d.]+:(\\d+)");
    private static final Pattern LISTEN_PORT = Pattern.compile("started on port(?:\\(s\\))?:? (\\d+)");
    private static final Pattern FETCH_URL = Pattern.compile("(?:Failed to fetch|from/to \\S+ \\(|Could not resolve host:?)\\s*(https?://[^\\s)'\"]+|[\\w-]+(?:\\.[\\w-]+)+)");
    private static final Pattern DB_TARGET = Pattern.compile("Connection to ([\\w.-]+):(\\d+) refused");
    private static final Pattern IMAGE = Pattern.compile("Failed to pull image \"([^\"]+)\"");

    private IncidentFacts() {
    }

    public static Enrichment enrich(String code, IncidentEvidence evidence) {
        Enrichment specific = switch (code == null ? "" : code) {
            case "COMPILATION_ERROR" -> compilation(evidence);
            case "TEST_FAILURE" -> testFailure(evidence);
            case "FRONTEND_BUILD" -> frontend(evidence);
            case "DOCKERFILE_INVALID" -> dockerfile(evidence);
            case "VULNERABILITY" -> vulnerability(evidence);
            case "CONFIG_MISSING" -> configMissing(evidence);
            case "DATABASE_UNREACHABLE" -> database(evidence);
            case "PROBE_FAILURE" -> probe(evidence);
            case "DEPENDENCY_DOWNLOAD" -> download(evidence);
            case "IMAGE_PULL" -> imagePull(evidence);
            case "CRASH_LOOP", "OUT_OF_MEMORY" -> crash(code, evidence);
            default -> null;
        };
        if (specific != null) {
            return specific;
        }
        return new Enrichment(List.of(), decisiveLine(evidence), null);
    }

    // ---------- Construction ----------

    private static Enrichment compilation(IncidentEvidence ev) {
        Hit m = first(JAVAC, ev.jenkinsLog());
        if (m == null) {
            return null;
        }
        return new Enrichment(List.of(new Fact("Fichier", m.group(1)), new Fact("Ligne", m.group(2)), new Fact("Erreur", m.group(4))),
                line(m), "Corriger " + m.group(1) + ", ligne " + m.group(2) + " : « " + m.group(4)
                        + " ». Vérifier ensuite en local (mvn compile) avant de redéployer.");
    }

    private static Enrichment testFailure(IncidentEvidence ev) {
        for (String raw : ev.jenkinsLog()) {
            Matcher m = SUREFIRE.matcher(raw);
            if (m.find() && !raw.contains("Tests run")) {
                String test = m.group(1) + "." + m.group(2);
                return new Enrichment(List.of(new Fact("Test en échec", test), new Fact("Ligne", m.group(3)), new Fact("Message", m.group(4))),
                        abbreviate(raw), "Le test " + test + " échoue (ligne " + m.group(3) + ") : « " + m.group(4)
                                + " ». Corriger le code testé, ou le test s'il est erroné, puis relancer mvn test.");
            }
        }
        return null;
    }

    private static Enrichment frontend(IncidentEvidence ev) {
        Hit m = first(TSC, ev.jenkinsLog());
        if (m == null) {
            return null;
        }
        return new Enrichment(List.of(new Fact("Fichier", m.group(1)), new Fact("Ligne", m.group(2)),
                new Fact("Erreur", m.group(4) + " : " + m.group(5))), line(m),
                "Corriger " + m.group(1) + ", ligne " + m.group(2) + " : « " + m.group(5) + " » (" + m.group(4)
                        + "). Vérifier en local (npm run type-check) avant de redéployer.");
    }

    private static Enrichment dockerfile(IncidentEvidence ev) {
        Hit instruction = first(DOCKER_INSTRUCTION, ev.jenkinsLog());
        if (instruction == null) {
            return null;
        }
        List<Fact> facts = new ArrayList<>(List.of(new Fact("Instruction inconnue", instruction.group(1))));
        Hit lineNo = first(DOCKER_LINE, ev.jenkinsLog());
        if (lineNo != null) {
            facts.add(new Fact("Ligne du Dockerfile", lineNo.group(1)));
        }
        return new Enrichment(facts, line(instruction), "Remplacer l'instruction « " + instruction.group(1)
                + " » du Dockerfile par une instruction valide (FROM, RUN, COPY, ENV, EXPOSE, ENTRYPOINT…).");
    }

    private static Enrichment vulnerability(IncidentEvidence ev) {
        List<Fact> facts = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        String library = null;
        String installed = null;
        String severity = "CRITICAL";
        for (String raw : ev.jenkinsLog()) {
            Matcher m = TRIVY_ROW.matcher(raw);
            if (!m.find() || facts.size() >= 3) {
                continue;
            }
            library = m.group(1).isBlank() ? library : m.group(1);
            installed = m.group(4).isBlank() ? installed : m.group(4);
            severity = m.group(3).isBlank() ? severity : m.group(3);
            String fixed = m.group(5).isBlank() ? "aucune version corrigée" : m.group(5);
            facts.add(new Fact(m.group(2) + " (" + severity + ")", library + " " + installed + " → " + fixed));
            updates.add(library + " " + installed + " → " + fixed);
        }
        if (facts.isEmpty()) {
            return null;
        }
        return new Enrichment(facts, null, "Mettre à jour vers la version corrigée : " + String.join(" ; ", updates.stream().distinct().toList())
                + ". Puis redéployer : Trivy refera le contrôle.");
    }

    private static Enrichment download(IncidentEvidence ev) {
        Hit m = first(FETCH_URL, ev.jenkinsLog());
        if (m == null) {
            return null;
        }
        String target = m.group(1);
        String host = target.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        return new Enrichment(List.of(new Fact("Ressource injoignable", target), new Fact("Serveur", host)), line(m),
                "Le téléchargement depuis " + host + " a échoué. Ce type d'échec est souvent passager : relancer le déploiement. "
                        + "S'il se répète, vérifier l'accès Internet de la VM et de ses conteneurs (DNS, proxy).");
    }

    // ---------- Exécution ----------

    private static Enrichment configMissing(IncidentEvidence ev) {
        List<String> all = concat(ev.containerLogs(), ev.podStates(), ev.events());
        Hit placeholder = first(PLACEHOLDER, all);
        Hit env = first(ENV_ABSENT, all);
        Hit secret = first(SECRET_NOT_FOUND, all);
        String name;
        String kind;
        Hit source;
        if (env != null) {
            name = env.group(1) != null ? env.group(1) : env.group(2);
            kind = "Variable d'environnement";
            source = env;
        } else if (placeholder != null) {
            name = placeholder.group(1);
            kind = "Propriété de configuration";
            source = placeholder;
        } else if (secret != null) {
            name = secret.group(1);
            kind = "Secret Kubernetes";
            source = secret;
        } else {
            return null;
        }
        return new Enrichment(List.of(new Fact(kind + " manquante", name)), line(source),
                "Définir « " + name + " » (" + kind.toLowerCase() + ") dans la configuration du déploiement, puis redéployer.");
    }

    private static Enrichment database(IncidentEvidence ev) {
        Hit m = first(DB_TARGET, ev.containerLogs());
        String target = m == null ? null : m.group(1) + ":" + m.group(2);
        List<Fact> facts = target == null ? List.of() : List.of(new Fact("Base visée", target));
        return new Enrichment(facts, m != null ? line(m) : decisiveLine(ev),
                "La base " + (target == null ? "" : target + " ") + "n'a pas répondu. Vérifier que son pod est prêt, puis l'utilisateur "
                        + "et le Secret du mot de passe. Au premier déploiement, l'application peut redémarrer le temps que la base démarre : "
                        + "ce n'est un problème que si cela persiste.");
    }

    private static Enrichment probe(IncidentEvidence ev) {
        Hit probed = first(PROBE_PORT, ev.events());
        if (probed == null) {
            return null;
        }
        String port = probed.group(1);
        Hit listening = first(LISTEN_PORT, ev.containerLogs());
        List<Fact> facts = new ArrayList<>(List.of(new Fact("Port sondé par Kubernetes", port)));
        String action;
        if (listening != null && !listening.group(1).equals(port)) {
            facts.add(new Fact("Port réellement écouté", listening.group(1)));
            action = "L'application écoute sur le port " + listening.group(1) + ", mais Kubernetes la sonde sur le port " + port
                    + " : redéployer avec le port " + listening.group(1) + ".";
        } else {
            action = "Kubernetes sonde le port " + port + " et personne n'y répond. Vérifier le port d'écoute réel de l'application "
                    + "(EXPOSE du Dockerfile : 8080 pour Nginx non privilégié et pour Spring Boot), puis redéployer avec ce port.";
        }
        return new Enrichment(facts, line(probed), action);
    }

    private static Enrichment imagePull(IncidentEvidence ev) {
        Hit m = first(IMAGE, concat(ev.events(), ev.podStates()));
        if (m == null) {
            return null;
        }
        return new Enrichment(List.of(new Fact("Image introuvable", m.group(1))), line(m),
                "Le cluster ne trouve pas l'image " + m.group(1) + " : vérifier qu'elle a bien été publiée (étape Docker Push) "
                        + "et que le cluster accède au registre.");
    }

    private static Enrichment crash(String code, IncidentEvidence ev) {
        List<Fact> facts = new ArrayList<>();
        Hit exit = first(EXIT_CODE, ev.podStates());
        Integer exitCode = exit == null ? null : Integer.valueOf(exit.group(2));
        if (exit != null) {
            facts.add(new Fact("Code de sortie", exitCode + " : " + EXIT_CODES.getOrDefault(exitCode, "erreur du programme")));
        }
        Hit uptime = first(UPTIME, ev.podStates());
        Integer seconds = uptime == null ? null : Integer.valueOf(uptime.group(1));
        if (seconds != null) {
            facts.add(new Fact("Moment de l'arrêt", seconds <= 30
                    ? "après " + seconds + " s : l'application échoue dès son démarrage"
                    : "après " + seconds + " s de fonctionnement : l'échec survient en cours d'exécution, pas au démarrage"));
        }
        String decisive = decisiveLine(ev);
        String action;
        if ("OUT_OF_MEMORY".equals(code) || Integer.valueOf(137).equals(exitCode)) {
            action = "Le conteneur a épuisé sa mémoire. Corriger la consommation (une fuite qui grossit avec le temps) ou augmenter la limite "
                    + "mémoire ; en attendant, revenir à la dernière version saine.";
        } else if (decisive != null) {
            action = "Corriger la cause donnée par la ligne décisive ci-dessus"
                    + (seconds != null && seconds > 30 ? ", qui survient en cours d'exécution" : "")
                    + ". En attendant, revenir à la dernière version saine.";
        } else {
            action = "Les logs du conteneur ne sont plus disponibles (pods remplacés). Consulter la première analyse, faite au moment "
                    + "de l'incident ; en attendant, revenir à la dernière version saine.";
        }
        return new Enrichment(facts, decisive, action);
    }

    // ---------- Outils ----------

    /** La ligne qui explique le mieux l'incident : l'exception la plus profonde, sinon la dernière erreur. */
    static String decisiveLine(IncidentEvidence ev) {
        String deepest = null;
        for (String raw : ev.containerLogs()) {
            if (CAUSED_BY.matcher(raw).find()) {
                deepest = raw;
            }
        }
        if (deepest != null) {
            return abbreviate(deepest.strip());
        }
        String lastError = null;
        for (String raw : ev.containerLogs()) {
            if (APP_ERROR.matcher(raw).find()) {
                lastError = raw;
            }
        }
        if (lastError != null) {
            return abbreviate(lastError.strip());
        }
        for (String raw : ev.jenkinsLog()) {
            if (raw.contains("[ERROR]") || raw.startsWith("ERROR:") || raw.contains(" ERROR: ")) {
                return abbreviate(raw.strip());
            }
        }
        return null;
    }

    /** Une correspondance, avec la ligne de preuve complète d'où elle vient. */
    private record Hit(Matcher matcher, String raw) {
        String group(int index) {
            return matcher.group(index);
        }
    }

    private static Hit first(Pattern pattern, List<String> lines) {
        for (String raw : lines) {
            Matcher m = pattern.matcher(raw);
            if (m.find()) {
                return new Hit(m, raw);
            }
        }
        return null;
    }

    private static String line(Hit hit) {
        return abbreviate(hit.raw().strip());
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        List<String> all = new ArrayList<>();
        for (List<String> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    private static String abbreviate(String text) {
        return IncidentAnalyzer.abbreviate(text);
    }
}

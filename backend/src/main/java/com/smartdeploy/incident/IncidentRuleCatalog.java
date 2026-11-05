package com.smartdeploy.incident;

import com.smartdeploy.incident.IncidentEvidence.Source;

import java.util.List;

import static com.smartdeploy.incident.IncidentRule.regex;

/**
 * Catalogue des pannes connues. Chaque signature combine l'étape du pipeline
 * concernée et des motifs caractéristiques des logs ou des événements.
 * L'ordre n'a pas d'importance : l'analyseur retient la meilleure correspondance.
 */
public final class IncidentRuleCatalog {

    private static final List<Source> BUILD = List.of(Source.JENKINS, Source.ERROR);
    private static final List<Source> RUNTIME = List.of(Source.POD, Source.EVENT, Source.CONTAINER, Source.JENKINS, Source.ERROR, Source.GUARD);

    public static final List<IncidentRule> RULES = List.of(
            new IncidentRule("COMPILATION_ERROR", "Erreur de compilation",
                    "Le code source ne compile pas : Maven signale une erreur dans un fichier Java.",
                    "Corriger l'erreur au fichier et à la ligne indiqués, puis vérifier la compilation en local (mvn compile).",
                    regex("Build Maven"),
                    List.of(regex("COMPILATION ERROR"), regex("\\.java:\\[\\d+,\\d+\\]"), regex("Failed to execute goal .*maven-compiler-plugin")),
                    BUILD),
            new IncidentRule("TEST_FAILURE", "Tests unitaires en échec",
                    "Au moins un test automatisé échoue : la version ne respecte pas le comportement attendu.",
                    "Consulter le test en échec cité dans les preuves, corriger le code ou le test, puis relancer les tests en local (mvn test).",
                    regex("Build Maven|Test"),
                    List.of(regex("Tests run: \\d+, Failures: [1-9]"), regex("Tests run: \\d+, Failures: \\d+, Errors: [1-9]"),
                            regex("There are test failures"), regex("<<< FAILURE!"), regex("maven-surefire-plugin.*failed")),
                    BUILD),
            new IncidentRule("DEPENDENCY_DOWNLOAD", "Téléchargement des dépendances impossible",
                    "Maven ou npm n'a pas pu télécharger une dépendance : problème réseau ou dépôt indisponible.",
                    "Vérifier l'accès Internet de Jenkins (DNS, proxy) et relancer le déploiement ; l'erreur est souvent passagère.",
                    regex("Build Maven|Docker Build"),
                    List.of(regex("Could not transfer artifact"), regex("Could not resolve dependencies"),
                            regex("UnknownHostException"), regex("npm ERR! (network|code ETIMEDOUT|code ECONNRESET)"),
                            regex("Connection (timed out|reset)")),
                    BUILD),
            new IncidentRule("QUALITY_GATE", "Porte de qualité SonarQube non franchie",
                    "Le code ne respecte pas les seuils de qualité définis dans SonarQube.",
                    "Ouvrir le rapport SonarQube du projet et corriger les problèmes qui bloquent la porte de qualité.",
                    regex("Quality Gate|SonarQube"),
                    List.of(regex("Quality Gate.*(ERROR|FAILED)"), regex("QUALITY GATE STATUS: FAILED"), regex("Pipeline aborted due to quality gate failure")),
                    BUILD),
            new IncidentRule("DOCKERFILE_INVALID", "Dockerfile invalide",
                    "La construction de l'image échoue : une instruction du Dockerfile est invalide ou un fichier copié est absent.",
                    "Corriger l'instruction citée dans le Dockerfile et vérifier les chemins des fichiers copiés.",
                    regex("Dockerfile|Docker Build"),
                    List.of(regex("unknown instruction"), regex("dockerfile parse error"),
                            regex("COPY failed"), regex("failed to compute cache key")),
                    BUILD),
            new IncidentRule("FRONTEND_BUILD", "Construction du frontend en échec",
                    "La compilation du frontend échoue : erreur TypeScript ou de construction de l'application.",
                    "Corriger l'erreur au fichier et à la ligne indiqués, puis vérifier avec npm run build en local.",
                    regex("Frontend"),
                    List.of(regex("error TS\\d+"), regex("npm ERR! code ELIFECYCLE"), regex("Build failed with \\d+ error")),
                    BUILD),
            new IncidentRule("VULNERABILITY", "Vulnérabilité critique détectée",
                    "Trivy a détecté une vulnérabilité de gravité critique dans l'image : la publication est refusée.",
                    "Mettre à jour la dépendance ou l'image de base citée vers une version corrigée, puis redéployer.",
                    regex("Trivy"),
                    List.of(regex("CRITICAL: [1-9]"), regex("CVE-\\d{4}-\\d+.*CRITICAL"), regex("│ *CRITICAL *│")),
                    BUILD),
            new IncidentRule("SCAN_TIMEOUT", "Analyse de sécurité trop longue",
                    "L'analyse Trivy n'a pas abouti dans le délai imparti, souvent à cause d'une image très volumineuse.",
                    "Réduire la taille de l'image (construction en plusieurs étapes, image de base légère) ou augmenter le délai d'analyse.",
                    regex("Trivy"),
                    // Uniquement les délais propres à l'analyse : « timeout » seul reconnaissait
                    // n'importe quelle option de délai du log (« --connect-timeout », « timeout=10 »).
                    List.of(regex("context deadline exceeded"), regex("(scan|analysis) (timed out|timeout)"),
                            regex("trivy.*(timed out|deadline)")),
                    BUILD),
            new IncidentRule("REGISTRY_UNAVAILABLE", "Registre d'images inaccessible",
                    "L'image n'a pas pu être publiée ou téléchargée : le registre Docker privé ne répond pas.",
                    "Vérifier que le registre fonctionne sur la machine virtuelle (port 5000) et relancer le déploiement.",
                    regex("Docker Push|Registry"),
                    List.of(regex("connection refused.*:5000"), regex("dial tcp .*:5000"), regex("no such host")),
                    BUILD),
            new IncidentRule("IMAGE_PULL", "Image introuvable par Kubernetes",
                    "Kubernetes ne parvient pas à télécharger l'image : référence erronée ou registre inaccessible depuis le cluster.",
                    "Vérifier la référence de l'image (empreinte) et la configuration du registre sur le nœud K3s.",
                    regex("Kubernetes"),
                    List.of(regex("ImagePullBackOff"), regex("ErrImagePull"), regex("Failed to pull image")),
                    RUNTIME),
            new IncidentRule("CONFIG_MISSING", "Configuration manquante",
                    "Le conteneur ne démarre pas faute d'un paramètre de configuration : variable d'environnement, Secret ou propriété absente.",
                    "Ajouter la variable ou le Secret cité dans les preuves à la configuration du déploiement.",
                    regex("Kubernetes"),
                    List.of(regex("CreateContainerConfigError"), regex("secret \".*\" not found"),
                            regex("Could not resolve placeholder"), regex("variable .* absente"), regex("configuration .* (absente|manquante)")),
                    RUNTIME),
            new IncidentRule("DATABASE_UNREACHABLE", "Base de données inaccessible",
                    "L'application ne parvient pas à se connecter à sa base de données.",
                    "Vérifier que la base est prête, et que l'hôte, le port, l'utilisateur et le Secret du mot de passe sont corrects.",
                    regex("Kubernetes"),
                    List.of(regex("Connection to .*:5432 refused"), regex("password authentication failed"),
                            regex("JDBCConnectionException"), regex("Unable to obtain JDBC Connection"), regex("PSQLException")),
                    RUNTIME),
            new IncidentRule("OUT_OF_MEMORY", "Mémoire insuffisante",
                    "L'application épuise sa mémoire : elle est arrêtée par la JVM ou par Kubernetes, puis redémarrée.",
                    "Rechercher une fuite mémoire dans la version déployée, ou augmenter la mémoire allouée si la consommation est légitime.",
                    regex("Kubernetes"),
                    List.of(regex("OOMKilled"), regex("OutOfMemoryError"), regex("Terminating due to java.lang.OutOfMemoryError"),
                            regex("Arrêt pour mémoire insuffisante : OOMKilled")),
                    RUNTIME),
            new IncidentRule("CRASH_LOOP", "L'application plante en boucle",
                    "Le conteneur s'arrête peu après son démarrage, puis Kubernetes le redémarre en boucle.",
                    "Lire l'exception citée dans les logs du conteneur ; revenir à la version précédente en attendant la correction.",
                    regex("Kubernetes"),
                    // « exit code » seul reconnaissait aussi des messages banals (PostgreSQL) :
                    // seul l'état de pod formaté par l'analyste est retenu.
                    List.of(regex("CrashLoopBackOff"), regex("Back-off restarting failed container"),
                            regex("dernier arrêt : Error \\(exit code [1-9]"),
                            regex("Redémarrages depuis le déploiement : [2-9]"), regex("Exception in thread \"main\""),
                            regex("APPLICATION FAILED TO START")),
                    RUNTIME).asSymptom(),
            new IncidentRule("PROBE_FAILURE", "Sondes de santé en échec",
                    "Le conteneur tourne mais ne répond pas aux sondes de Kubernetes dans les délais : démarrage trop lent ou port incorrect.",
                    "Vérifier que l'application écoute sur le port déclaré et ajuster les délais des sondes si le démarrage est lent.",
                    regex("Kubernetes"),
                    List.of(regex("(Startup|Readiness|Liveness) probe failed"), regex("connect: connection refused")),
                    RUNTIME).asSymptom()
    );

    private IncidentRuleCatalog() {
    }
}

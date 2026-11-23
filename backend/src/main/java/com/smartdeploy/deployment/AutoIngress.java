package com.smartdeploy.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

/**
 * Adresse publique automatique : chaque environnement reçoit un nom d'hôte unique, dérivé de son espace de
 * noms, sous un domaine « joker » (par exemple nip.io, qui renvoie vers l'adresse IP qu'il contient).
 * Un nom saisi à la main est respecté ; un nom automatique est recalculé (une promotion ne doit pas
 * reprendre l'adresse de l'environnement d'origine).
 */
public final class AutoIngress {

    private AutoIngress() {
    }

    /** « sd-test-demo-complete » + « 192.168.85.129.nip.io » → « test-demo-complete.192.168.85.129.nip.io ». */
    public static String host(String namespace, String domain) {
        String base = namespace.toLowerCase(Locale.ROOT).replaceFirst("^sd-", "").replaceAll("[^a-z0-9-]", "-");
        if (base.length() > 50) {
            base = base.substring(0, 50).replaceAll("-+$", "");
        }
        return base + "." + domain.toLowerCase(Locale.ROOT);
    }

    /** Applique l'adresse automatique à la configuration, si aucune adresse n'a été choisie à la main. */
    public static void apply(ObjectNode config, String namespace, String domain) {
        if (domain == null || domain.isBlank() || namespace == null) {
            return;
        }
        if (!config.hasNonNull("frontend") && !config.hasNonNull("backend")) {
            return;
        }
        JsonNode ingress = config.get("ingress");
        String current = ingress != null && ingress.hasNonNull("host") ? ingress.get("host").asText("") : "";
        boolean manual = !current.isBlank() && !current.endsWith("." + domain.toLowerCase(Locale.ROOT));
        if (manual) {
            return;
        }
        ObjectNode node = ingress instanceof ObjectNode o ? o : config.putObject("ingress");
        node.put("host", host(namespace, domain));
    }
}

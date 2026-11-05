package com.smartdeploy.dora;

/**
 * Un indicateur DORA : sa valeur, son unité, son niveau de performance
 * (ELITE, HIGH, MEDIUM, LOW ou NONE sans donnée) et sa définition exacte.
 */
public record DoraMetric(String key, String label, Double value, String unit, String level, String detail, String definition) {
}

package com.smartdeploy.confidence;

/** Un facteur du score : les points retirés et leur justification. */
public record ConfidenceFactor(String label, int points, String explanation) {
}

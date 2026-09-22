package com.smartdeploy.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import com.smartdeploy.monitoring.dto.MetricPoint;

import java.util.ArrayList;
import java.util.List;

@Component
public class PrometheusClient {

    private final RestClient restClient;

    public PrometheusClient(PrometheusProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .build();
    }

    public String query(String promQlQuery) {

        String encodedQuery = UriUtils.encodeQueryParam(
                promQlQuery,
                StandardCharsets.UTF_8
        );

        URI uri = URI.create(
                "/api/v1/query?query=" + encodedQuery
        );

        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }

    public double querySingleValue(String promQlQuery) {

        String response = query(promQlQuery);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response);

            JsonNode results = root
                    .path("data")
                    .path("result");

            if (!results.isArray() || results.isEmpty()) {
                return 0.0;
            }

            JsonNode value = results
                    .get(0)
                    .path("value");

            if (!value.isArray() || value.size() < 2) {
                return 0.0;
            }

            return Double.parseDouble(value.get(1).asText());

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible de lire la réponse Prometheus",
                    exception
            );
        }
    }
    public String queryRange(
            String promQlQuery,
            long start,
            long end,
            int step
    ) {

        String encodedQuery = UriUtils.encodeQueryParam(
                promQlQuery,
                StandardCharsets.UTF_8
        );

        URI uri = URI.create(
                "/api/v1/query_range" +
                        "?query=" + encodedQuery +
                        "&start=" + start +
                        "&end=" + end +
                        "&step=" + step
        );

        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }
    public List<MetricPoint> queryRangePoints(
            String promQlQuery,
            long start,
            long end,
            int step
    ) {

        String response = queryRange(
                promQlQuery,
                start,
                end,
                step
        );

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response);

            JsonNode results = root
                    .path("data")
                    .path("result");

            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }

            JsonNode values = results
                    .get(0)
                    .path("values");

            if (!values.isArray()) {
                return List.of();
            }

            List<MetricPoint> points = new ArrayList<>();

            for (JsonNode value : values) {

                if (!value.isArray() || value.size() < 2) {
                    continue;
                }

                long timestamp = value.get(0).asLong();
                double metricValue =
                        Double.parseDouble(value.get(1).asText());

                points.add(
                        new MetricPoint(
                                timestamp,
                                metricValue
                        )
                );
            }

            return points;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible de lire l'historique Prometheus",
                    exception
            );
        }
    }
}
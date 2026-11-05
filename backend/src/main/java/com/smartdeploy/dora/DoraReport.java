package com.smartdeploy.dora;

import java.time.LocalDateTime;
import java.util.List;

public record DoraReport(int periodDays, LocalDateTime from, LocalDateTime to, List<DoraMetric> metrics, List<String> notes) {
}

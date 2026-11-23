package com.smartdeploy.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScorecardSnapshotRepository extends JpaRepository<ScorecardSnapshot, Long> {

    List<ScorecardSnapshot> findTop10ByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}

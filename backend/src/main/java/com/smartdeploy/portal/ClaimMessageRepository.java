package com.smartdeploy.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimMessageRepository extends JpaRepository<ClaimMessage, Long> {

    List<ClaimMessage> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}

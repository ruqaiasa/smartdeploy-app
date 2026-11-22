package com.smartdeploy.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /** Réclamations visibles par un développeur : les siennes, et celles qui portent sur ses applications. */
    @Query("SELECT c FROM Claim c WHERE c.requester.id = :userId OR c.application.owner.id = :userId ORDER BY c.createdAt DESC")
    List<Claim> findVisibleTo(@Param("userId") Long userId);

    List<Claim> findAllByOrderByCreatedAtDesc();

    List<Claim> findByStatusIn(Collection<Claim.Status> statuses);

    List<Claim> findByStatusAndResolvedAtBefore(Claim.Status status, LocalDateTime before);
}

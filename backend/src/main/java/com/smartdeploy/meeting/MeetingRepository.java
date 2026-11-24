package com.smartdeploy.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    @Query("SELECT m FROM Meeting m WHERE m.organizer.id = :userId OR m.invitee.id = :userId ORDER BY m.createdAt DESC")
    List<Meeting> findForUser(@Param("userId") Long userId);
}

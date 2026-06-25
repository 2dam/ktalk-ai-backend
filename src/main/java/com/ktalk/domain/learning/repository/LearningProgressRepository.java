package com.ktalk.domain.learning.repository;

import com.ktalk.domain.learning.entity.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningProgressRepository extends JpaRepository<LearningProgress, String> {
    // userId를 Long으로 변경
    List<LearningProgress> findByUserIdAndStatus(Long userId, LearningProgress.Status status);
    List<LearningProgress> findByUserIdOrderByStartedAtDesc(Long userId);
    Optional<LearningProgress> findByUserIdAndVideoId(Long userId, String videoId);

    @Query("SELECT COUNT(l) FROM LearningProgress l WHERE l.user.id = :userId AND l.status = :status")
    long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") LearningProgress.Status status);

    @Query("SELECT l FROM LearningProgress l WHERE l.user.id = :userId AND l.status != :status ORDER BY l.lastViewedAt DESC")
    List<LearningProgress> findActiveLearningByUserId(@Param("userId") Long userId, @Param("status") LearningProgress.Status status);
}
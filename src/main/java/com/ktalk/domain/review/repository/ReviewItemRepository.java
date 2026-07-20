package com.ktalk.domain.review.repository;

import com.ktalk.domain.review.entity.ReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, String> {

    List<ReviewItem> findByUserIdOrderByNextReviewAtAsc(Long userId);

    /** 지금 복습해야 하는(알람이 울려야 하는) 항목들. */
    List<ReviewItem> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            Long userId, LocalDateTime now);

    long countByUserIdAndNextReviewAtLessThanEqual(Long userId, LocalDateTime now);

    boolean existsByUserIdAndSentence(Long userId, String sentence);

    Optional<ReviewItem> findByIdAndUserId(String id, Long userId);
}

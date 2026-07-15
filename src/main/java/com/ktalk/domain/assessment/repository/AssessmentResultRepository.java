package com.ktalk.domain.assessment.repository;

import com.ktalk.domain.assessment.entity.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    Optional<AssessmentResult> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}

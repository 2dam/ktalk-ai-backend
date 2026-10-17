package com.ktalk.domain.feedback.repository;

import com.ktalk.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}

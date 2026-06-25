package com.ktalk.domain.learning.repository;

import com.ktalk.domain.learning.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, String> {
    List<Quiz> findByLearningProgressId(String learningProgressId);
    List<Quiz> findByLearningProgressIdAndAnsweredCorrectly(String learningProgressId, Boolean correct);
    long countByLearningProgressIdAndAnsweredCorrectly(String learningProgressId, Boolean correct);
}
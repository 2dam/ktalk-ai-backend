package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.curriculum.entity.Curriculum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurriculumRepository extends JpaRepository<Curriculum, String> {

    Optional<Curriculum> findByLearnerType(LearnerType learnerType);
}

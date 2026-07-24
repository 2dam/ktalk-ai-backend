package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.CurriculumProblem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumProblemRepository extends JpaRepository<CurriculumProblem, String> {
}

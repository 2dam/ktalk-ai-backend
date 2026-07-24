package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.CurriculumPassage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumPassageRepository extends JpaRepository<CurriculumPassage, String> {
}

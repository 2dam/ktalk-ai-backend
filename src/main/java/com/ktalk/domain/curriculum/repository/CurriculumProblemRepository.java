package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.CurriculumProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumProblemRepository extends JpaRepository<CurriculumProblem, String> {

    @Query("select count(p) from CurriculumProblem p where p.passage.day.curriculum.id = :curriculumId")
    long countByCurriculumId(@Param("curriculumId") String curriculumId);
}

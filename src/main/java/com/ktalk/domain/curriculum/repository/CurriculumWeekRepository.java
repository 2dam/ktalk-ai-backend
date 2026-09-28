package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.CurriculumWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurriculumWeekRepository extends JpaRepository<CurriculumWeek, String> {

    List<CurriculumWeek> findByCurriculumIdOrderByWeekNumberAsc(String curriculumId);
}

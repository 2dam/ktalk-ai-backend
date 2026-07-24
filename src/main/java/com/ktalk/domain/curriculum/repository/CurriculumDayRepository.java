package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.CurriculumDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurriculumDayRepository extends JpaRepository<CurriculumDay, String> {

    Optional<CurriculumDay> findByCurriculumIdAndDayNumber(String curriculumId, int dayNumber);
}

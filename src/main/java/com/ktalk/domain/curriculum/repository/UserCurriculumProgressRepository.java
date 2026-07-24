package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.UserCurriculumProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCurriculumProgressRepository extends JpaRepository<UserCurriculumProgress, String> {

    Optional<UserCurriculumProgress> findByUserId(Long userId);
}

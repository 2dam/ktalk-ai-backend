package com.ktalk.domain.topik.repository;

import com.ktalk.domain.topik.entity.UserTopikProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTopikProgressRepository extends JpaRepository<UserTopikProgress, String> {

    Optional<UserTopikProgress> findByUserId(Long userId);
}

package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.curriculum.entity.UserWrongAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserWrongAnswerRepository extends JpaRepository<UserWrongAnswer, String> {

    List<UserWrongAnswer> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<UserWrongAnswer> findByUser_IdAndProblem_Id(Long userId, String problemId);

    void deleteByUser_IdAndProblem_Id(Long userId, String problemId);
}

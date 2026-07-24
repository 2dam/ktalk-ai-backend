package com.ktalk.domain.topik.repository;

import com.ktalk.domain.topik.entity.QuizItem;
import com.ktalk.domain.topik.entity.TopikLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizItemRepository extends JpaRepository<QuizItem, String> {

    List<QuizItem> findByTopikLevel(TopikLevel topikLevel);

    List<QuizItem> findByTopikLevelIn(List<TopikLevel> topikLevels);

    boolean existsByWordId(String wordId);
}

package com.ktalk.domain.topik.repository;

import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, String> {

    Optional<Word> findByText(String text);

    List<Word> findByTopikLevel(TopikLevel topikLevel);

    List<Word> findByTopikLevelIn(List<TopikLevel> topikLevels);

    List<Word> findByTopikLevelAndIdNot(TopikLevel topikLevel, String id);
}

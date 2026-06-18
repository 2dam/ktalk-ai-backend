package com.ktalk.domain.content.repository;

import com.ktalk.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {

    // 예시 메서드들
    Optional<Content> findByTitle(String title);

    @Query("SELECT c FROM Content c WHERE c.category = :category")
    List<Content> findByCategory(@Param("category") String category);
}
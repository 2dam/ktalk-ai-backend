package com.ktalk.domain.block.repository;

import com.ktalk.domain.block.entity.TagEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagEmbeddingRepository extends JpaRepository<TagEmbedding, String> {
    Optional<TagEmbedding> findByTag(String tag);
}

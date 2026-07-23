package com.ktalk.domain.block.repository;

import com.ktalk.domain.block.entity.LearningBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningBlockRepository extends JpaRepository<LearningBlock, String> {

    @Query("SELECT DISTINCT b FROM LearningBlock b JOIN b.outputTags t WHERE t = :tag")
    List<LearningBlock> findByOutputTag(@Param("tag") String tag);
}

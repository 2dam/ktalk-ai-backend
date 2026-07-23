package com.ktalk.domain.block.repository;

import com.ktalk.domain.block.entity.AssemblyBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssemblyBlockRepository extends JpaRepository<AssemblyBlock, String> {
    List<AssemblyBlock> findByBlock_Id(String blockId);
}

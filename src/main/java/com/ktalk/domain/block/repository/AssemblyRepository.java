package com.ktalk.domain.block.repository;

import com.ktalk.domain.block.entity.Assembly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssemblyRepository extends JpaRepository<Assembly, String> {
    List<Assembly> findByUserIdOrderByCreatedAtDesc(Long userId);
}

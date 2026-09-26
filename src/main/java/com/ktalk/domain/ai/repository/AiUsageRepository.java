package com.ktalk.domain.ai.repository;

import com.ktalk.domain.ai.entity.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiUsageRepository extends JpaRepository<AiUsage, String> {

    /** 한도 미만일 때만 원자적으로 1 증가시킨다. 영향받은 행 수(0 또는 1)를 돌려준다. */
    @Transactional
    @Modifying
    @Query("update AiUsage u set u.count = u.count + 1 "
            + "where u.usageKey = :key and u.month = :month and u.count < :limit")
    int incrementIfBelowLimit(@Param("key") String key, @Param("month") String month, @Param("limit") int limit);
}

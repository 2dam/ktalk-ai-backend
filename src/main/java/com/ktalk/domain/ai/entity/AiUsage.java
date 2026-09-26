package com.ktalk.domain.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(또는 비로그인 IP)별 월간 AI 기능 사용 횟수. (usageKey, month)당 한 행만 두고
 * 호출할 때마다 count를 올린다.
 */
@Entity
@Table(name = "ai_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"usage_key", "usage_month"}))
@Getter
@NoArgsConstructor
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "usage_key", nullable = false)
    private String usageKey;

    /** yyyy-MM (Asia/Seoul 기준) */
    @Column(name = "usage_month", nullable = false, length = 7)
    private String month;

    @Column(nullable = false)
    private int count;

    public AiUsage(String usageKey, String month) {
        this.usageKey = usageKey;
        this.month = month;
        this.count = 1;
    }
}

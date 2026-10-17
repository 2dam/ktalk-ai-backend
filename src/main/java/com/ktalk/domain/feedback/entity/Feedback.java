package com.ktalk.domain.feedback.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자가 앱 안의 "의견 보내기"로 남긴 피드백.
 * DB에 원본을 남기고, 설정되어 있으면 GitHub 이슈로도 전달한다(운영자 알림·요약용).
 */
@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** bug | idea | other */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 2000)
    private String message;

    /** 피드백을 보낸 화면 경로 (예: /topik) */
    @Column(length = 200)
    private String page;

    /** 로그인 사용자가 보냈으면 사용자 ID, 아니면 null */
    private Long userId;

    @Column(length = 300)
    private String userAgent;

    /** GitHub 이슈로 전달됐으면 그 이슈 번호 */
    private Integer githubIssueNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

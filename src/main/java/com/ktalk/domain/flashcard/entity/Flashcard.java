package com.ktalk.domain.flashcard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 플래시카드. ecue의 뒤집기 복습 방식을 한국어 학습용으로 차용.
 * 앞면(front)=한국어 단어/문장, 뒷면(back)=뜻. SM-2 간격 반복으로 복습 주기를 자동 조정한다.
 *
 * <p>카드는 손으로 채우지 않아도 찬다:
 *  - 클립 학습(ClipAndLearn)에서 학습자가 하이라이트한 표현
 *  - 지문 로드 시 ClickableKorean이 추출한 단어
 *  - 튜터/커리큘럼에서 "이 지문의 핵심 단어를 카드로 만들어줘" 요청 시 배치 생성
 */
@Entity
@Table(name = "flashcards")
@Getter
@Setter
@NoArgsConstructor
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 카드 소유자 (로그인 사용자). 학습 진도는 개인별로 누적된다.
    private String userId;

    @Column(nullable = false)
    private String front;   // 한국어 단어/문장

    @Column(nullable = false)
    private String back;    // 뜻 (모국어)

    private String audioBase64; // TTS 음성 (선택)

    private String source;  // 출처 (예: "clip:영상ID", "lesson:관심사")

    // SM-2 간격 반복 상태
    private int boxLevel = 1;             // 1~5 (높을수록 잘 앎)
    private LocalDateTime nextReviewAt;   // 다음 복습 예정 시각

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.nextReviewAt == null) this.nextReviewAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * SM-2 기반 복습 결과 반영. quality: 0(모름)~5(완벽).
     * 맞추면 boxLevel 상승 + 다음 복습 지연, 틀리면 boxLevel 초기화 + 즉시 재복습.
     */
    public void applyReview(int quality) {
        if (quality >= 3) {
            this.boxLevel = Math.min(5, this.boxLevel + 1);
        } else {
            this.boxLevel = 1;
        }
        // 간격(일): box1=0, box2=1, box3=3, box4=7, box5=16
        int[] intervals = {0, 1, 3, 7, 16};
        int days = intervals[Math.max(0, Math.min(4, this.boxLevel - 1))];
        this.nextReviewAt = LocalDateTime.now().plusDays(days);
    }
}

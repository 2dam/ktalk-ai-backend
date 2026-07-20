package com.ktalk.domain.review.service;

import com.ktalk.domain.review.dto.ReviewItemResponse;
import com.ktalk.domain.review.dto.SaveReviewItemRequest;
import com.ktalk.domain.review.entity.ReviewItem;
import com.ktalk.domain.review.repository.ReviewItemRepository;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewItemRepository reviewItemRepository;
    private final UserRepository userRepository;

    /**
     * 방금 배운 문장을 복습 목록에 추가한다. 같은 사용자가 같은 문장을 이미 저장했으면
     * 중복 저장하지 않고 기존 항목을 돌려준다.
     */
    @Transactional
    public ReviewItemResponse saveLearned(Long userId, SaveReviewItemRequest request) {
        if (request.sentence() == null || request.sentence().isBlank()) {
            throw new IllegalArgumentException("복습할 문장이 비어 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();

        if (reviewItemRepository.existsByUserIdAndSentence(userId, request.sentence())) {
            ReviewItem existing = reviewItemRepository
                    .findByUserIdOrderByNextReviewAtAsc(userId).stream()
                    .filter(it -> request.sentence().equals(it.getSentence()))
                    .findFirst()
                    .orElseThrow();
            return ReviewItemResponse.from(existing, now);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));

        ReviewItem item = new ReviewItem();
        item.setUser(user);
        item.setInterest(request.interest());
        item.setSentence(request.sentence());
        item.setMeaning(request.meaning());
        item.setPattern(request.pattern());
        item.setSensoryWord(request.sensoryWord());
        // repetitions=0, intervalDays=0, easeFactor=2.5, nextReviewAt=내일 (엔티티 @PrePersist)

        ReviewItem saved = reviewItemRepository.save(item);
        log.info("복습 항목 저장: userId={}, sentence='{}'", userId, request.sentence());
        return ReviewItemResponse.from(saved, now);
    }

    /** 지금 복습해야 하는 항목(알람 대상). */
    @Transactional(readOnly = true)
    public List<ReviewItemResponse> getDue(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return reviewItemRepository
                .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(userId, now)
                .stream()
                .map(item -> ReviewItemResponse.from(item, now))
                .toList();
    }

    /** 사용자의 전체 복습 항목(예정 포함). */
    @Transactional(readOnly = true)
    public List<ReviewItemResponse> getAll(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return reviewItemRepository
                .findByUserIdOrderByNextReviewAtAsc(userId)
                .stream()
                .map(item -> ReviewItemResponse.from(item, now))
                .toList();
    }

    /** 알람 배지에 쓸, 지금 복습할 문장 개수. */
    @Transactional(readOnly = true)
    public long dueCount(Long userId) {
        return reviewItemRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, LocalDateTime.now());
    }

    /** 복습 카드 한 장을 채점하고 SM-2로 다음 복습 시각을 갱신한다. */
    @Transactional
    public ReviewItemResponse grade(Long userId, String itemId, int quality) {
        ReviewItem item = reviewItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("복습 항목을 찾을 수 없습니다: " + itemId));
        item.applyReview(quality);
        ReviewItem saved = reviewItemRepository.save(item);
        return ReviewItemResponse.from(saved, LocalDateTime.now());
    }

    /** 복습 항목 삭제(더 이상 복습하지 않기). */
    @Transactional
    public void delete(Long userId, String itemId) {
        ReviewItem item = reviewItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("복습 항목을 찾을 수 없습니다: " + itemId));
        reviewItemRepository.delete(item);
    }
}

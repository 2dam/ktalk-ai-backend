package com.ktalk.domain.learning.service;

import com.ktalk.domain.learning.entity.LearningProgress;
import com.ktalk.domain.learning.repository.LearningProgressRepository;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningService {

    private final LearningProgressRepository progressRepository;
    private final UserRepository userRepository;

    @Transactional
    public LearningProgress startLearning(Long userId, String videoId, String videoTitle,
                                          String thumbnailUrl, String channelName) {
        // 이미 진행 중인 학습이 있는지 확인
        return progressRepository.findByUserIdAndVideoId(userId, videoId)
                .filter(progress -> progress.getStatus() != LearningProgress.Status.COMPLETED)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다. ID: " + userId));

                    LearningProgress progress = new LearningProgress();
                    progress.setUser(user);
                    progress.setVideoId(videoId);
                    progress.setVideoTitle(videoTitle);
                    progress.setThumbnailUrl(thumbnailUrl);
                    progress.setChannelName(channelName);
                    progress.startLearning();

                    log.info("새 학습 시작: userId={}, videoId={}", userId, videoId);
                    return progressRepository.save(progress);
                });
    }

    @Transactional
    public LearningProgress updateProgress(String progressId, int watchTimeSeconds, int totalVideoSeconds) {
        LearningProgress progress = progressRepository.findById(progressId)
                .orElseThrow(() -> new RuntimeException("학습 기록을 찾을 수 없습니다."));

        progress.updateProgress(watchTimeSeconds, totalVideoSeconds);
        log.info("학습 진행률 업데이트: progressId={}, percent={}%", progressId, progress.getProgressPercent());
        return progressRepository.save(progress);
    }

    @Transactional
    public LearningProgress completeLearning(String progressId) {
        LearningProgress progress = progressRepository.findById(progressId)
                .orElseThrow(() -> new RuntimeException("학습 기록을 찾을 수 없습니다."));

        progress.completeLearning();
        log.info("학습 완료: progressId={}", progressId);
        return progressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public List<LearningProgress> getUserProgress(Long userId) {
        return progressRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<LearningProgress> getActiveLearning(Long userId) {
        return progressRepository.findActiveLearningByUserId(userId, LearningProgress.Status.COMPLETED);
    }

    @Transactional(readOnly = true)
    public long getCompletedCount(Long userId) {
        return progressRepository.countByUserIdAndStatus(userId, LearningProgress.Status.COMPLETED);
    }
}
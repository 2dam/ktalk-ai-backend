package com.ktalk.domain.topik.service;

import com.ktalk.domain.topik.dto.ProgressResponse;
import com.ktalk.domain.topik.dto.QuizItemResponse;
import com.ktalk.domain.topik.dto.SubmitAnswerResponse;
import com.ktalk.domain.topik.entity.QuizItem;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.UserTopikProgress;
import com.ktalk.domain.topik.repository.QuizItemRepository;
import com.ktalk.domain.topik.repository.UserTopikProgressRepository;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 사용자의 최근 정답률로 상(5-6급)/중(3-4급)/하(1-2급) 수준을 추정하고, 그 수준 안에서
 * 다음 문항을 고른다. 성공확률이 50%에 가까운 문항일수록 정보량(엔트로피)이 크다는
 * 직관을 빌리되, 학습 동기가 꺾이지 않도록 목표 성공률을 50%보다 살짝 쉬운 쪽(0.65)으로
 * 잡았다 — 아직 안 풀어본 문항은 정보가 없으므로 우선 노출해 성공률 추정치부터 쌓는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveQuizService {

    private static final double TARGET_SUCCESS_RATE = 0.65;

    private final QuizItemRepository quizItemRepository;
    private final UserTopikProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final Random random = new Random();

    @Transactional
    public UserTopikProgress getOrCreateProgress(Long userId) {
        return progressRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
            UserTopikProgress progress = new UserTopikProgress();
            progress.setUser(user);
            progress.setTopikLevel(TopikLevel.LEVEL_1);
            return progressRepository.save(progress);
        });
    }

    @Transactional
    public QuizItemResponse nextQuestion(Long userId) {
        UserTopikProgress progress = getOrCreateProgress(userId);
        List<QuizItem> pool = quizItemRepository.findByTopikLevel(progress.getTopikLevel());
        if (pool.isEmpty()) {
            List<TopikLevel> groupLevels = Arrays.stream(TopikLevel.values())
                    .filter(level -> level.getGroup() == progress.getTopikLevel().getGroup())
                    .toList();
            pool = quizItemRepository.findByTopikLevelIn(groupLevels);
        }
        if (pool.isEmpty()) {
            throw new IllegalStateException("출제할 수 있는 문항이 없습니다: " + progress.getTopikLevel());
        }

        List<QuizItem> unseen = pool.stream().filter(item -> item.getAttemptCount() == 0).toList();
        QuizItem chosen = unseen.isEmpty()
                ? pickClosestToTarget(pool)
                : unseen.get(random.nextInt(unseen.size()));

        return QuizItemResponse.from(chosen);
    }

    private QuizItem pickClosestToTarget(List<QuizItem> pool) {
        return pool.stream()
                .min(Comparator.comparingDouble(item -> Math.abs(item.successRate() - TARGET_SUCCESS_RATE)))
                .orElseThrow();
    }

    @Transactional
    public SubmitAnswerResponse submitAnswer(Long userId, String itemId, int selectedIndex) {
        QuizItem item = quizItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("문항을 찾을 수 없습니다: " + itemId));
        UserTopikProgress progress = getOrCreateProgress(userId);

        boolean correct = selectedIndex == item.getCorrectAnswerIndex();
        item.recordAttempt(correct);
        quizItemRepository.save(item);

        boolean levelChanged = progress.recordAnswer(correct);
        progressRepository.save(progress);

        if (levelChanged) {
            log.info("사용자 {} TOPIK 등급 변경: {}({})", userId,
                    progress.getTopikLevel().getDisplayName(), progress.getTopikLevel().getGroup().getLabel());
        }

        String correctAnswerText = item.getOptions().get(item.getCorrectAnswerIndex());
        return new SubmitAnswerResponse(
                correct,
                correctAnswerText,
                progress.getTopikLevel(),
                progress.getTopikLevel().getGroup(),
                levelChanged,
                progress.getAttemptCount(),
                progress.getCorrectCount()
        );
    }

    @Transactional
    public ProgressResponse getProgress(Long userId) {
        UserTopikProgress progress = getOrCreateProgress(userId);
        return new ProgressResponse(
                progress.getTopikLevel(),
                progress.getTopikLevel().getGroup(),
                progress.getAttemptCount(),
                progress.getCorrectCount(),
                progress.accuracy()
        );
    }
}

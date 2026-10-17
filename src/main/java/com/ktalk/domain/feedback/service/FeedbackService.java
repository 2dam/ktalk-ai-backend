package com.ktalk.domain.feedback.service;

import com.ktalk.domain.feedback.dto.FeedbackRequest;
import com.ktalk.domain.feedback.entity.Feedback;
import com.ktalk.domain.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final Set<String> CATEGORIES = Set.of("bug", "idea", "other");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "bug", "버그", "idea", "제안", "other", "기타");
    private static final int REQUESTS_PER_WINDOW = 5;
    private static final long WINDOW_MS = 10 * 60_000L;

    private final FeedbackRepository feedbackRepository;
    private final WebClient webClient;
    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    // GitHub 이슈 전달용. 토큰이 비어 있으면 DB 저장만 한다.
    @Value("${app.feedback.github-token:}")
    private String githubToken;

    @Value("${app.feedback.github-repo:2dam/ktalk-ai-backend}")
    private String githubRepo;

    /** 같은 접속자(IP)가 10분에 5건 넘게 보내지 못하게 막는다. */
    public boolean tryAcquire(String clientKey) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestHistory.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < now - WINDOW_MS) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= REQUESTS_PER_WINDOW) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    public Feedback submit(FeedbackRequest request, Long userId, String userAgent) {
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("내용을 입력해 주세요.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("내용은 " + MAX_MESSAGE_LENGTH + "자 이내로 적어 주세요.");
        }
        String category = request.category() != null && CATEGORIES.contains(request.category())
                ? request.category() : "other";

        Feedback feedback = new Feedback();
        feedback.setCategory(category);
        feedback.setMessage(message);
        feedback.setPage(truncate(request.page(), 200));
        feedback.setUserId(userId);
        feedback.setUserAgent(truncate(userAgent, 300));
        Feedback saved = feedbackRepository.save(feedback);

        forwardToGithub(saved);
        return saved;
    }

    /**
     * 저장된 피드백을 GitHub 이슈(라벨: feedback)로 만든다. 저장소가 공개일 수 있으므로
     * 사용자 이름·이메일 같은 개인정보는 넣지 않는다. 실패해도 사용자 요청은 성공으로 둔다.
     */
    private void forwardToGithub(Feedback feedback) {
        if (githubToken == null || githubToken.isBlank()) {
            return;
        }
        String label = CATEGORY_LABELS.getOrDefault(feedback.getCategory(), "기타");
        String preview = feedback.getMessage().replaceAll("\\s+", " ");
        if (preview.length() > 50) {
            preview = preview.substring(0, 50) + "…";
        }
        String body = """
                **분류:** %s
                **화면:** %s
                **로그인:** %s
                **접수:** %s (피드백 #%d)

                ---

                %s
                """.formatted(
                label,
                feedback.getPage() == null || feedback.getPage().isBlank() ? "-" : "`" + feedback.getPage() + "`",
                feedback.getUserId() != null ? "예" : "아니오",
                feedback.getCreatedAt(),
                feedback.getId(),
                feedback.getMessage());

        Map<String, Object> payload = Map.of(
                "title", "[피드백/" + label + "] " + preview,
                "body", body,
                "labels", List.of("feedback", "feedback:" + feedback.getCategory()));

        webClient.post()
                .uri("https://api.github.com/repos/" + githubRepo + "/issues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .subscribe(
                        response -> {
                            Object number = response.get("number");
                            if (number instanceof Number n) {
                                feedback.setGithubIssueNumber(n.intValue());
                                feedbackRepository.save(feedback);
                            }
                        },
                        error -> log.warn("피드백 #{} GitHub 이슈 전달 실패: {}", feedback.getId(), error.getMessage()));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}

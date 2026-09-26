package com.ktalk.domain.ai.service;

import com.ktalk.domain.ai.entity.AiUsage;
import com.ktalk.domain.ai.repository.AiUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 무료 서비스에서 AI 호출 비용이 무한정 늘지 않도록 사용자(비로그인은 IP)별 월간 사용 횟수를
 * 제한한다. 서버 재시작/스핀다운에도 유지되도록 DB에 저장한다. 이 클래스는 일부러
 * @Transactional을 쓰지 않는다 — 최초 INSERT가 동시 요청으로 유니크 제약에 걸려도
 * (Postgres는 실패한 문장이 트랜잭션 전체를 중단시키므로) 바깥 트랜잭션이 없어야 바로
 * UPDATE를 재시도할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AiUsageService {

    private final AiUsageRepository repository;

    @Value("${AI_MONTHLY_LIMIT:300}")
    private int monthlyLimit;

    public int getMonthlyLimit() {
        return monthlyLimit;
    }

    /** 이번 달 사용 가능하면 횟수를 1 올리고 true, 한도를 넘었으면 false. */
    public boolean tryConsume(String usageKey) {
        String month = YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
        if (repository.incrementIfBelowLimit(usageKey, month, monthlyLimit) > 0) {
            return true;
        }
        try {
            repository.save(new AiUsage(usageKey, month));
            return monthlyLimit >= 1;
        } catch (DataIntegrityViolationException raced) {
            // 이미 행이 있다(첫 호출이 아님 또는 동시에 생성됨): 한도 미만일 때만 다시 증가.
            return repository.incrementIfBelowLimit(usageKey, month, monthlyLimit) > 0;
        }
    }
}

package com.ktalk.config;

import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;
import com.ktalk.domain.topik.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TOPIK 적응형 퀴즈를 GEMINI_API_KEY 없이도 바로 테스트해볼 수 있도록 등급별 샘플
 * 단어를 심어둔다. 오답 선택지 생성(임베딩 API 호출)은 여기서 하지 않고,
 * POST /api/topik/words/{id}/quiz-item 호출 시점에 API 키가 있을 때 만들도록 남겨둔다.
 */
@Component
@RequiredArgsConstructor
public class TopikDataLoader implements CommandLineRunner {

    private final WordRepository wordRepository;

    private record Seed(String text, String meaning, TopikLevel level) {}

    @Override
    public void run(String... args) {
        if (wordRepository.count() > 0) {
            return;
        }

        List<Seed> seeds = List.of(
                new Seed("가다", "어떤 곳으로 이동하다", TopikLevel.LEVEL_1),
                new Seed("먹다", "음식을 입으로 씹어서 삼키다", TopikLevel.LEVEL_1),
                new Seed("학교", "학생들이 공부하는 곳", TopikLevel.LEVEL_2),
                new Seed("친구", "가깝게 오래 사귄 사람", TopikLevel.LEVEL_2),
                new Seed("경제", "돈과 재화를 생산하고 소비하는 활동 전체", TopikLevel.LEVEL_3),
                new Seed("환경", "생물이 살아가는 주변의 조건이나 상태", TopikLevel.LEVEL_3),
                new Seed("문화", "한 사회가 공유하는 생활 방식과 가치", TopikLevel.LEVEL_4),
                new Seed("정책", "정부나 단체가 문제 해결을 위해 세우는 방침", TopikLevel.LEVEL_4),
                new Seed("함양", "지식이나 능력을 길러서 갖추는 것", TopikLevel.LEVEL_5),
                new Seed("규명하다", "어떤 사실을 자세히 따져서 밝히다", TopikLevel.LEVEL_5),
                new Seed("파급효과", "어떤 일이 다른 분야까지 영향을 미치는 결과", TopikLevel.LEVEL_6),
                new Seed("실효성", "실제로 효과를 나타내는 성질", TopikLevel.LEVEL_6)
        );

        for (Seed seed : seeds) {
            Word word = new Word();
            word.setText(seed.text());
            word.setMeaning(seed.meaning());
            word.setTopikLevel(seed.level());
            wordRepository.save(word);
        }
        System.out.println("✅ TOPIK 샘플 단어 " + seeds.size() + "개 생성 완료 (하/중/상급 각 4개)");
    }
}

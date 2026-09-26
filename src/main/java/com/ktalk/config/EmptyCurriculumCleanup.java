package com.ktalk.config;

import com.ktalk.domain.curriculum.entity.Curriculum;
import com.ktalk.domain.curriculum.repository.CurriculumDayRepository;
import com.ktalk.domain.curriculum.repository.CurriculumProblemRepository;
import com.ktalk.domain.curriculum.repository.CurriculumRepository;
import com.ktalk.domain.curriculum.repository.UserCurriculumProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문제가 하나도 없는 커리큘럼(예: 예전 시각적 몰입형 "4~5급" 골격 커리큘럼)을 지운다. 학습유형별
 * 1~2/3~4/5~6급 전용 로더가 그 자리를 완전히 대체했는데 이미 배포된 DB에는 빈 껍데기가 남아
 * 있어서, 데이터가 비어 있는 커리큘럼이 배정될 여지를 없앤다. 모든 시더가 끝난 뒤(가장 나중에)
 * 실행되고, 내용이 있는 커리큘럼은 건드리지 않는다.
 */
@Component
@RequiredArgsConstructor
@Order(1000)
public class EmptyCurriculumCleanup implements CommandLineRunner {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;
    private final CurriculumProblemRepository curriculumProblemRepository;
    private final UserCurriculumProgressRepository progressRepository;

    @Override
    @Transactional
    public void run(String... args) {
        for (Curriculum curriculum : curriculumRepository.findAll()) {
            if (curriculumProblemRepository.countByCurriculumId(curriculum.getId()) > 0) {
                continue;
            }
            progressRepository.deleteByCurriculumId(curriculum.getId());
            // 일자는 curriculum과 week 양쪽을 참조하므로 주차가 먼저 지워지지 않게 일자부터 지운다.
            curriculumDayRepository.deleteAll(curriculumDayRepository.findByCurriculumId(curriculum.getId()));
            curriculumDayRepository.flush();
            curriculumRepository.delete(curriculum);
            System.out.println("🧹 문제가 없는 빈 커리큘럼 삭제: " + curriculum.getTitle()
                    + " (" + curriculum.getTargetLevelLabel() + ")");
        }
    }
}

package com.ktalk.domain.curriculum.repository;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.curriculum.entity.Curriculum;
import com.ktalk.domain.topik.entity.TopikLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurriculumRepository extends JpaRepository<Curriculum, String> {

    /** VISUAL_IMMERSIVE/EXPERIENTIAL_ACTOR처럼 급수단계 구분 없이 학습유형당 커리큘럼이 하나뿐인 경우용. */
    Optional<Curriculum> findByLearnerType(LearnerType learnerType);

    /** 학습유형 + 급수단계(targetLevelFrom)로 정확히 하나의 커리큘럼을 찾는다(예: STRATEGIC_ANALYST + LEVEL_3 = 3~4급 과정). */
    Optional<Curriculum> findByLearnerTypeAndTargetLevelFrom(LearnerType learnerType, TopikLevel targetLevelFrom);

    /** 학습유형에 급수단계별 커리큘럼이 여러 개 있을 때, 가장 낮은 급수단계(신규 사용자 시작점)를 찾는다. */
    Optional<Curriculum> findFirstByLearnerTypeOrderByTargetLevelFromAsc(LearnerType learnerType);
}

package com.ktalk.domain.topik.dto;

import com.ktalk.domain.topik.entity.QuizItem;
import com.ktalk.domain.topik.entity.TopikGroup;
import com.ktalk.domain.topik.entity.TopikLevel;

import java.util.List;

/** 정답 인덱스는 채점 전까지 클라이언트에 내려주지 않는다. */
public record QuizItemResponse(
        String id,
        String question,
        List<String> options,
        TopikLevel topikLevel,
        TopikGroup topikGroup
) {
    public static QuizItemResponse from(QuizItem item) {
        return new QuizItemResponse(
                item.getId(),
                item.getQuestion(),
                item.getOptions(),
                item.getTopikLevel(),
                item.getTopikLevel().getGroup()
        );
    }
}

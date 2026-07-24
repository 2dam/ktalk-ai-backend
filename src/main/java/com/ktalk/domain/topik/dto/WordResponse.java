package com.ktalk.domain.topik.dto;

import com.ktalk.domain.topik.entity.TopikGroup;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;

public record WordResponse(
        String id,
        String text,
        String meaning,
        String exampleSentence,
        TopikLevel topikLevel,
        TopikGroup topikGroup
) {
    public static WordResponse from(Word word) {
        TopikLevel level = word.getTopikLevel();
        return new WordResponse(
                word.getId(),
                word.getText(),
                word.getMeaning(),
                word.getExampleSentence(),
                level,
                level == null ? null : level.getGroup()
        );
    }
}

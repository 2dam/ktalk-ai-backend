package com.ktalk.domain.topik.service;

import com.ktalk.domain.topik.dto.DistractorResponse;
import com.ktalk.domain.topik.dto.QuizItemResponse;
import com.ktalk.domain.topik.entity.QuizItem;
import com.ktalk.domain.topik.entity.Word;
import com.ktalk.domain.topik.repository.QuizItemRepository;
import com.ktalk.domain.topik.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 단어 하나로부터 객관식 퀴즈 문항을 만든다. 아직 난이도 분류가 안 된 단어면 먼저
 * TopikLevelClassifierService로 분류하고, DistractorGeneratorService가 고른 오답을
 * 정답(단어의 뜻)과 섞어 보기를 구성한다. 두 기능을 하나의 문항 생성 흐름으로 묶는 지점이다.
 */
@Service
@RequiredArgsConstructor
public class QuizItemService {

    private static final int OPTION_COUNT = 4;

    private final WordRepository wordRepository;
    private final QuizItemRepository quizItemRepository;
    private final WordService wordService;
    private final DistractorGeneratorService distractorGeneratorService;

    @Transactional
    public QuizItemResponse createFromWord(String wordId) {
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new IllegalArgumentException("단어를 찾을 수 없습니다: " + wordId));
        if (word.getTopikLevel() == null) {
            wordService.classify(wordId);
            word = wordRepository.findById(wordId).orElseThrow();
        }
        if (word.getMeaning() == null || word.getMeaning().isBlank()) {
            throw new IllegalStateException("뜻이 없는 단어는 퀴즈로 만들 수 없습니다: " + word.getText());
        }

        List<DistractorResponse> distractors = distractorGeneratorService.generate(wordId, OPTION_COUNT - 1);
        if (distractors.isEmpty()) {
            throw new IllegalStateException("오답 후보를 찾지 못해 퀴즈를 만들 수 없습니다: " + word.getText());
        }

        List<String> options = new ArrayList<>();
        options.add(word.getMeaning());
        distractors.forEach(d -> options.add(d.meaning()));
        Collections.shuffle(options);
        int correctIndex = options.indexOf(word.getMeaning());

        QuizItem item = new QuizItem();
        item.setWord(word);
        item.setQuestion("다음 단어의 뜻으로 알맞은 것은? \"" + word.getText() + "\"");
        item.setOptions(options);
        item.setCorrectAnswerIndex(correctIndex);
        item.setTopikLevel(word.getTopikLevel());

        QuizItem saved = quizItemRepository.save(item);
        return QuizItemResponse.from(saved);
    }
}

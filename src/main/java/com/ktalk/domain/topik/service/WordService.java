package com.ktalk.domain.topik.service;

import com.ktalk.domain.topik.dto.WordCreateRequest;
import com.ktalk.domain.topik.dto.WordResponse;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;
import com.ktalk.domain.topik.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WordService {

    private final WordRepository wordRepository;
    private final TopikLevelClassifierService classifierService;

    @Transactional
    public WordResponse create(WordCreateRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("단어가 비어 있습니다.");
        }
        Word word = wordRepository.findByText(request.text().strip()).orElseGet(Word::new);
        word.setText(request.text().strip());
        word.setMeaning(request.meaning());
        word.setExampleSentence(request.exampleSentence());
        Word saved = wordRepository.save(word);
        return WordResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WordResponse> list(TopikLevel topikLevel) {
        List<Word> words = topikLevel == null
                ? wordRepository.findAll()
                : wordRepository.findByTopikLevel(topikLevel);
        return words.stream().map(WordResponse::from).toList();
    }

    /** 단어를 상(5-6급)/중(3-4급)/하(1-2급) 난이도로 자동 분류해 저장한다. */
    @Transactional
    public WordResponse classify(String wordId) {
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new IllegalArgumentException("단어를 찾을 수 없습니다: " + wordId));
        TopikLevel level = classifierService.classify(word.getText(), word.getMeaning());
        word.setTopikLevel(level);
        Word saved = wordRepository.save(word);
        log.info("단어 난이도 분류: '{}' → {}({})", word.getText(), level.getDisplayName(), level.getGroup().getLabel());
        return WordResponse.from(saved);
    }
}

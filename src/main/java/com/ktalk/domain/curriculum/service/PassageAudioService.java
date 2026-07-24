package com.ktalk.domain.curriculum.service;

import com.ktalk.domain.ai.service.TTSService;
import com.ktalk.domain.curriculum.dto.AudioSegmentResponse;
import com.ktalk.domain.curriculum.entity.CurriculumPassage;
import com.ktalk.domain.curriculum.entity.PassageCategory;
import com.ktalk.domain.curriculum.repository.CurriculumPassageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 듣기 지문("남자:"/"여자:" 화자 표시가 있는 대화 스크립트)을 기존 TTSService로
 * 음성 합성한다. TTSService.synthesizeDialogue의 화자 구분 규칙은 화자명이 "A"면
 * 남성, 그 외면 여성이라(ContentManager의 대화 재생과 같은 규칙), 한국어 화자
 * 표시를 A/B로 바꿔서 넘긴다. 결과는 TTS 자체 캐시가 있어 같은 지문을 다시
 * 요청하면 API를 다시 호출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PassageAudioService {

    private static final Map<String, String> SPEAKER_TAG = Map.of("남자", "A", "여자", "B");

    private final CurriculumPassageRepository passageRepository;
    private final TTSService ttsService;

    @Transactional(readOnly = true)
    public List<AudioSegmentResponse> generateAudio(String passageId) {
        CurriculumPassage passage = passageRepository.findById(passageId)
                .orElseThrow(() -> new IllegalArgumentException("지문을 찾을 수 없습니다: " + passageId));
        if (passage.getCategory() != PassageCategory.LISTENING) {
            throw new IllegalStateException("듣기 지문이 아니라 음성을 만들 수 없습니다: " + passageId);
        }

        String taggedDialogue = tagSpeakers(passage.getPassageText());
        // title/description을 비워도 synthesizeDialogue가 빈 "." 안내 세그먼트를 하나
        // 만들어내므로(내부적으로 "title + '. ' + description" 형태를 가정), 그 흔적만 걸러낸다.
        return ttsService.synthesizeDialogue("", "", taggedDialogue, false).stream()
                .filter(segment -> !".".equals(segment.get("text")))
                .map(segment -> new AudioSegmentResponse(
                        segment.get("speaker"), segment.get("gender"), segment.get("text"), segment.get("audioContent")))
                .toList();
    }

    private String tagSpeakers(String passageText) {
        StringBuilder result = new StringBuilder();
        for (String line : passageText.split("\n")) {
            String converted = line;
            for (var entry : SPEAKER_TAG.entrySet()) {
                String prefix = entry.getKey() + ":";
                if (line.startsWith(prefix)) {
                    converted = entry.getValue() + ":" + line.substring(prefix.length());
                    break;
                }
            }
            result.append(converted).append("\n");
        }
        return result.toString();
    }
}

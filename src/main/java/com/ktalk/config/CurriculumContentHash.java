package com.ktalk.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 커리큘럼 시드 내용의 지문(SHA-256). 시드 레코드(WeekSeed 등)의 toString은 하위 문항·보기까지
 * 결정적으로 전부 찍으므로, 그 문자열의 해시가 같으면 콘텐츠가 바뀌지 않았다는 뜻이다.
 * 각 CurriculumDataLoader가 부팅 때마다 지우고 다시 심던 것을, 내용이 바뀐 때만 다시 심도록
 * 하는 데 쓴다(다시 심으면 문항 ID가 새로 만들어져 사용자 진도와 오답노트가 지워진다).
 */
final class CurriculumContentHash {

    /** 시드를 DB에 넣는 방식(예: 보기 섞기)이 바뀌면 올려서 기존 커리큘럼을 한 번 다시 심게 한다. */
    private static final String SEED_PIPELINE_VERSION = "shuffle-v1";

    private CurriculumContentHash() {}

    static String of(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(SEED_PIPELINE_VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            for (Object part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

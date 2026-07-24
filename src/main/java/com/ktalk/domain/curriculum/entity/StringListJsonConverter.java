package com.ktalk.domain.curriculum.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * List&lt;String&gt; 필드를 별도 컬렉션 테이블 없이 TEXT 컬럼 하나에 JSON으로 저장한다.
 * CurriculumProblem처럼 새 연관 테이블이 여러 개 한꺼번에 생기는 엔티티에서, Hibernate의
 * ddl-auto=update가 테이블 생성 순서를 잘못 잡아 "relation does not exist" 오류를 내는
 * 것을 피하기 위해 컬렉션 테이블 대신 이 방식을 쓴다.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("리스트를 JSON으로 변환하지 못했습니다.", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("JSON을 리스트로 변환하지 못했습니다.", e);
        }
    }
}

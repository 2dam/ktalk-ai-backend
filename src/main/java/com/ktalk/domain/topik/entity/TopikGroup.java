package com.ktalk.domain.topik.entity;

/**
 * TOPIK 6개 등급을 하급/중급/상급 3단계로 묶은 것.
 * 콘텐츠 난이도 조절과 적응형 출제는 이 단위로 크게 갈래를 나눈 뒤, 세부 조정은
 * TopikLevel(1~6급) 단위로 한다.
 */
public enum TopikGroup {

    LOWER("하급", 1, 2),
    MIDDLE("중급", 3, 4),
    UPPER("상급", 5, 6);

    private final String label;
    private final int minGrade;
    private final int maxGrade;

    TopikGroup(String label, int minGrade, int maxGrade) {
        this.label = label;
        this.minGrade = minGrade;
        this.maxGrade = maxGrade;
    }

    public String getLabel() {
        return label;
    }

    public int getMinGrade() {
        return minGrade;
    }

    public int getMaxGrade() {
        return maxGrade;
    }
}

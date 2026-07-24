package com.ktalk.domain.topik.entity;

/**
 * TOPIK 1~6급. 각 등급은 TopikGroup(하/중/상급) 하나에 속한다.
 */
public enum TopikLevel {

    LEVEL_1(1, TopikGroup.LOWER),
    LEVEL_2(2, TopikGroup.LOWER),
    LEVEL_3(3, TopikGroup.MIDDLE),
    LEVEL_4(4, TopikGroup.MIDDLE),
    LEVEL_5(5, TopikGroup.UPPER),
    LEVEL_6(6, TopikGroup.UPPER);

    private final int grade;
    private final TopikGroup group;

    TopikLevel(int grade, TopikGroup group) {
        this.grade = grade;
        this.group = group;
    }

    public int getGrade() {
        return grade;
    }

    public TopikGroup getGroup() {
        return group;
    }

    public String getDisplayName() {
        return grade + "급";
    }

    public static TopikLevel fromGrade(int grade) {
        for (TopikLevel level : values()) {
            if (level.grade == grade) {
                return level;
            }
        }
        throw new IllegalArgumentException("유효하지 않은 TOPIK 등급: " + grade);
    }

    /** 다음 등급(6급이면 자기 자신). */
    public TopikLevel up() {
        return grade < 6 ? fromGrade(grade + 1) : this;
    }

    /** 이전 등급(1급이면 자기 자신). */
    public TopikLevel down() {
        return grade > 1 ? fromGrade(grade - 1) : this;
    }
}

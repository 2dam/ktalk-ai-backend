package com.ktalk.domain.user.entity;

public class User {
}
package com.ktalk.domain.user.entity;

import jakarta.persistence.*;
        import lombok.*;
        import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    // 사업계획서 반영: 글로벌 타겟을 위한 모국어
    @Column(length = 10)
    private String nativeLanguage; // 예: EN, JP, VN, TH, ID, BR, FR

    // 사업계획서 반영: 관심 K-콘텐츠 장르
    @Column(length = 100)
    private String favoriteGenre; // 예: K-POP, K-DRAMA, K-FILM

    // 학습 레벨 (초급/중급/고급)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningLevel level = LearningLevel.BEGINNER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder
    public User(String email, String password, String nickname,
                String nativeLanguage, String favoriteGenre, LearningLevel level) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.nativeLanguage = nativeLanguage;
        this.favoriteGenre = favoriteGenre;
        this.level = level;
        this.role = Role.USER;
    }

    public enum Role {
        USER, ADMIN
    }

    public enum LearningLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    public void updatePassword(String password) {
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfile(String nickname, String favoriteGenre, LearningLevel level) {
        this.nickname = nickname;
        this.favoriteGenre = favoriteGenre;
        this.level = level;
        this.updatedAt = LocalDateTime.now();
    }
}
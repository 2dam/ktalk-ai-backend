package com.ktalk.domain.user.service;

public class UserService {
}
package com.ktalk.domain.user.service;

import com.ktalk.domain.user.dto.UserSignUpRequest;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입 처리
     * 사업계획서 반영: 글로벌 타겟을 위한 모국어, 관심 장르 저장
     */
    @Transactional
    public Long signUp(UserSignUpRequest request) {
        // 1. 이메일 중복 체크
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 3. 사용자 엔티티 생성 및 저장
        User user = User.builder()
                .email(request.email())
                .password(encodedPassword)
                .nickname(request.nickname())
                .nativeLanguage(request.nativeLanguage())
                .favoriteGenre(request.favoriteGenre())
                .level(parseLearningLevel(request.level()))
                .build();

        User savedUser = userRepository.save(user);
        return savedUser.getId();
    }

    /**
     * 이메일로 사용자 조회
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일의 사용자를 찾을 수 없습니다."));
    }

    /**
     * 학습 레벨 문자열을 Enum으로 변환
     */
    private User.LearningLevel parseLearningLevel(String level) {
        if (level == null || level.isBlank()) {
            return User.LearningLevel.BEGINNER;
        }
        try {
            return User.LearningLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return User.LearningLevel.BEGINNER;
        }
    }
}
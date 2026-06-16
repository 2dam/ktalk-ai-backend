package com.ktalk.domain.user.dto;

public class UserSignUpRequest {
}
package com.ktalk.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 회원가입 요청 시 사용할 데이터 형식 (Record 타입)
public record UserSignUpRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        String nickname,

        // 사업계획서 반영: 글로벌 타겟을 위한 모국어
        String nativeLanguage,

        // 사업계획서 반영: 관심 K-콘텐츠 장르
        String favoriteGenre,

        // 학습 레벨 (BEGINNER, INTERMEDIATE, ADVANCED)
        String level
) {}
package com.ktalk.domain.user.service;

import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        if (email == null) {
            throw new OAuth2AuthenticationException("Google 계정에서 이메일 정보를 가져올 수 없습니다.");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUsername(uniqueUsername(name != null && !name.isBlank() ? name : email));
            newUser.setProvider("GOOGLE");
            return userRepository.save(newUser);
        });

        return new CustomOAuth2User(oAuth2User, user.getId());
    }

    // username에는 유니크 제약이 있는데 구글 표시 이름은 겹칠 수 있다(동명이인, 기존 계정과
    // 같은 이름 등). 겹치면 제약 위반으로 가입/로그인이 실패하므로 숫자를 붙여 빈 이름을 찾는다.
    private String uniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String candidate = base + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
    }
}

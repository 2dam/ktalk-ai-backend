package com.ktalk.config;

import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        // 사용자가 하나도 없으면 테스트 사용자 생성 (ID는 IDENTITY 전략으로 자동 채번되어 1이 됨)
        if (userRepository.count() == 0) {
            User user = new User();
            user.setUsername("testuser");
            user.setPassword("password");
            user.setEmail("test@example.com");
            userRepository.save(user);
            System.out.println("✅ 테스트 사용자 생성 완료 (ID: " + user.getId() + ")");
        }
    }
}
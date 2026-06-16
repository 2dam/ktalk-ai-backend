package com.ktalk.domain.user.repository;

public interface UserRepository {
}
package com.ktalk.domain.user.repository;

import com.ktalk.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository를 상속받아야 DB 연동 기능이 자동으로 생깁니다.
public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 사용자를 찾는 메서드
    Optional<User> findByEmail(String email);

    // 이메일이 이미 존재하는지 확인하는 메서드 (중복 가입 방지)
    boolean existsByEmail(String email);

    // 닉네임으로 사용자를 찾는 메서드
    Optional<User> findByNickname(String nickname);
}
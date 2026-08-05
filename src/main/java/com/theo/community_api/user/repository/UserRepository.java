package com.theo.community_api.user.repository;

import com.theo.community_api.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // email로 유저 조회
    Optional<User> findByEmail(String email);

    // email 중복여부 확인
    boolean existsByEmail(String email);

    // nickname 중복여부 확인
    boolean existsByNickname(String nickname);

    @Query("""
            select u.profileImageKey
            from User u
            where u.profileImageKey is not null
            """)
    List<String> findAllProfileImageKeys();
}

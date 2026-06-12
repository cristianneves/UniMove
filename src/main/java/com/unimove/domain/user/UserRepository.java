package com.unimove.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("select u.status from User u where u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") UUID id);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    long countByRole(Role role);

    long countByStatus(UserStatus status);
}

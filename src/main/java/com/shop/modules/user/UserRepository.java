package com.shop.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndActive(UserRole role, boolean active);
    boolean existsByPhone(String phone);
}
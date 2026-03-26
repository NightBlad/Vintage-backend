package com.example.vintage.repository;

import com.example.vintage.entity.User;
import com.example.vintage.entity.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);

    long countByRolesName(RoleName name);

    Boolean existsByUsername(String username);
    
    Boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.username = ?1")
    Optional<User> findByUsernameWithRoles(String username);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);

    // Add method to update login attempts without triggering validation
    @Modifying
    @Query("UPDATE User u SET u.failedAttempts = :failedAttempts, u.accountLocked = :accountLocked, u.lockTime = :lockTime WHERE u.username = :username")
    void updateLoginAttempts(@Param("username") String username,
                           @Param("failedAttempts") Integer failedAttempts,
                           @Param("accountLocked") Boolean accountLocked,
                           @Param("lockTime") java.time.LocalDateTime lockTime);
}

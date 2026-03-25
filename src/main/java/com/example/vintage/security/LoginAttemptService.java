package com.example.vintage.security;

import com.example.vintage.entity.User;
import com.example.vintage.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5; // Tối đa 5 lần thử
    private static final int LOCKOUT_DURATION_MINUTES = 15; // Khóa 15 phút

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordFailedAttempt(String username) {
        User user = findUserByUsernameOrEmail(username);
        if (user != null) {
            // Check if lockout has expired
            if (user.isAccountLocked() && isLockoutExpired(user.getLockTime())) {
                // Reset if lockout has expired
                user.setFailedAttempts(1);
                user.setAccountLocked(false);
                user.setLockTime(null);
            } else {
                // Increment failed attempts
                int attempts = user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);

                if (attempts >= MAX_ATTEMPTS) {
                    user.setAccountLocked(true);
                    user.setLockTime(LocalDateTime.now());
                }
            }
            userRepository.save(user);
        }
    }

    @Transactional
    public void recordSuccessfulLogin(String username) {
        User user = findUserByUsernameOrEmail(username);
        if (user != null) {
            // Use direct update to avoid validation issues
            userRepository.updateLoginAttempts(user.getUsername(), 0, false, null);
        }
    }

    @Transactional
    public boolean isAccountLocked(String username) {
        User user = findUserByUsernameOrEmail(username);
        if (user == null) {
            return false;
        }

        // Check if lockout has expired
        if (user.isAccountLocked() && user.getLockTime() != null && isLockoutExpired(user.getLockTime())) {
            // Auto-unlock if expired
            unlockAccount(user);
            return false;
        }

        return user.isAccountLocked();
    }

    public int getRemainingAttempts(String username) {
        User user = findUserByUsernameOrEmail(username);
        if (user == null) {
            return MAX_ATTEMPTS;
        }

        if (user.getLockTime() != null && isLockoutExpired(user.getLockTime())) {
            return MAX_ATTEMPTS;
        }

        return Math.max(0, MAX_ATTEMPTS - user.getFailedAttempts());
    }

    public long getRemainingLockoutMinutes(String username) {
        User user = findUserByUsernameOrEmail(username);
        if (user == null || !user.isAccountLocked() || user.getLockTime() == null) {
            return 0;
        }

        LocalDateTime unlockTime = user.getLockTime().plusMinutes(LOCKOUT_DURATION_MINUTES);
        return ChronoUnit.MINUTES.between(LocalDateTime.now(), unlockTime);
    }

    private void unlockAccount(User user) {
        userRepository.updateLoginAttempts(user.getUsername(), 0, false, null);
    }

    private boolean isLockoutExpired(LocalDateTime lastAttemptTime) {
        if (lastAttemptTime == null) return true;
        return ChronoUnit.MINUTES.between(lastAttemptTime, LocalDateTime.now()) >= LOCKOUT_DURATION_MINUTES;
    }

    private User findUserByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsername(usernameOrEmail)
                .orElse(userRepository.findByEmail(usernameOrEmail).orElse(null));
    }
}

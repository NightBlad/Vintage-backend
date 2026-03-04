package com.example.vintage.config;

import com.example.vintage.security.LoginAttemptService;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        if (event instanceof AuthenticationSuccessEvent) {
            String username = event.getAuthentication().getName();
            loginAttemptService.recordSuccessfulLogin(username);
        } else if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            String username = event.getAuthentication().getName();
            loginAttemptService.recordFailedAttempt(username);
        }
    }
}

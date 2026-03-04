package com.example.vintage.config;

import com.example.vintage.service.SessionService;
import com.example.vintage.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private CartService cartService;

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn() {
        return sessionService.isLoggedIn();
    }

    @ModelAttribute("currentUser")
    public Object getCurrentUser() {
        return sessionService.getCurrentUser();
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return sessionService.isAdmin();
    }

    @ModelAttribute("currentUsername")
    public String getCurrentUsername() {
        return sessionService.getCurrentUsername();
    }

    @ModelAttribute("currentFullName")
    public String getCurrentFullName() {
        return sessionService.getCurrentFullName();
    }

    @ModelAttribute("cartItemCount")
    public int getCartItemCount() {
        return cartService.getTotalItems();
    }
}

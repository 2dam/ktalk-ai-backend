package com.ktalk.domain.user.service;

import lombok.Getter;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User delegate;
    private final Long userId;

    public CustomOAuth2User(OAuth2User delegate, Long userId) {
        this.delegate = delegate;
        this.userId = userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}

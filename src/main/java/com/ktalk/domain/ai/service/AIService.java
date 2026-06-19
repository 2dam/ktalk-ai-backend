package com.ktalk.domain.ai.service;

import com.ktalk.domain.content.entity.Content;

public interface AIService {
    Content generateContent(String topic);
}
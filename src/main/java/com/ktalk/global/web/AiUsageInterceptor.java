package com.ktalk.global.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.service.AiUsageService;
import com.ktalk.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** AI(Gemini 등) 호출 엔드포인트에 월간 사용 한도를 적용한다. WebMvcConfig에서 경로를 지정한다. */
@Component
@RequiredArgsConstructor
public class AiUsageInterceptor implements HandlerInterceptor {

    private final AiUsageService aiUsageService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        if (aiUsageService.tryConsume(usageKey(request))) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                "이번 달 AI 기능 사용 한도(" + aiUsageService.getMonthlyLimit() + "회)를 모두 사용했어요. 다음 달에 다시 이용해주세요."));
        return false;
    }

    // JwtAuthenticationFilter가 유효한 토큰의 사용자 ID를 principal로 세팅해둔다.
    private String usageKey(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? "user:" + userId : "ip:" + RequestUtils.clientKey(request);
    }
}

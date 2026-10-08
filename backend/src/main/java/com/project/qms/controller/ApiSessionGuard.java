package com.project.qms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.qms.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One rule, applied to every API call: you must be logged in.
 *
 * WHY THIS EXISTS. The write endpoints already load the current user so that
 * they can record who did what, but the read endpoints - the product list, the
 * defect list, the reports - would happily answer anybody who knew the address.
 * Adding a session check to each of those fifteen methods would work until
 * somebody added a sixteenth and forgot.
 *
 * So the rule is stated once, here, for everything under /api/ except the login
 * call itself. An unauthenticated request is stopped before it reaches any
 * controller and is answered with the same ApiError shape as every other
 * failure (FR-12.8), so the frontend keeps one error handler.
 *
 * WHAT THIS IS NOT. It is not a security framework. Role-level rules - who may
 * verify a defect, who may create a batch - are business rules and live in the
 * services (Phase 4 §6.2). This class only answers "is anybody logged in?".
 */
@Component
public class ApiSessionGuard implements HandlerInterceptor, WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public ApiSessionGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");    // you cannot require a session to log in
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getSession(false) != null
                && request.getSession(false).getAttribute(AuthController.SESSION_USER_ID) != null) {
            return true;                                    // logged in - carry on
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                "You are not logged in. Please log in and try again.",
                List.of(),
                LocalDateTime.now())));
        return false;
    }
}

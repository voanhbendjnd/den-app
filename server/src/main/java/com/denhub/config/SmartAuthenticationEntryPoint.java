package com.denhub.config;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.denhub.security.UserNotActivatedException;

import java.io.IOException;
/*
 * This method called when request must include token (anyAuthenticated), but not found token or token expired
 * Return 401
 * BadCredential -> through this class
 * */
@Component
public class SmartAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public SmartAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        String message = "(Session expired) Please login to access this resource";
        String code = "UNAUTHORIZED";
        String redirectUrl = "/login";
        Throwable rootCause = authException.getCause();
        if(rootCause instanceof UserNotActivatedException) {
            message = rootCause.getMessage();
            code = "USER_NOT_ACTIVATED";
            redirectUrl = "/verify-account";
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message);
        // Keep legacy fields for existing clients while exposing Problem Details.
        problem.setProperty("error", "Unauthorized");
        problem.setProperty("message", message);
        problem.setProperty("code", code);
        problem.setProperty("publicApi", false);
        problem.setProperty("redirect", true);
        problem.setProperty("redirectUrl", redirectUrl);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

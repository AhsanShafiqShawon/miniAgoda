package com.miniagoda.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniagoda.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response,
                      HttpServletRequest request,
                      int status,
                      String error,
                      String message) throws IOException {
        ErrorResponse body = ErrorResponse.of(status, error, message, request.getRequestURI());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
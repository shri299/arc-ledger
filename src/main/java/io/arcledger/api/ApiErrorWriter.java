package io.arcledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.api.ApiModels.ErrorResponse;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String message) throws IOException {
        if (response.isCommitted()) return;
        response.resetBuffer();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(code, message, Instant.now(), RequestIds.current(request)));
    }
}

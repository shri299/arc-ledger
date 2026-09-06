package io.arcledger.security;

import io.arcledger.api.RequestIds;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(-110)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String incoming = request.getHeader("X-Request-ID");
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
            ? incoming : UUID.randomUUID().toString();
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }
}

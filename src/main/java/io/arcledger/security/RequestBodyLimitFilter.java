package io.arcledger.security;

import io.arcledger.api.ApiErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Order(-90)
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ApiErrorWriter errors;

    public RequestBodyLimitFilter(@Value("${arcledger.http.max-request-body-bytes:131072}") int maxBytes,
                                  ApiErrorWriter errors) {
        this.maxBytes = maxBytes;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return !("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            errors.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "The request body exceeds the allowed size.");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            errors.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "The request body exceeds the allowed size.");
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(),
                getCharacterEncoding() == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(getCharacterEncoding())));
        }
    }
}

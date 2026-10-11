package foodapi.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청마다 ID를 정하고(로그 MDC + 응답 헤더), 요청이 끝나면 한 줄로 기록한다.
 * 가장 먼저 실행되어 예외 처리 로그를 포함한 모든 로그에 요청 ID가 붙는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // 클라이언트가 보낸 ID는 안전한 문자만 허용한다 (로그 위조 방지)
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    // 호출이 잦거나 정보가 없는 경로는 요청 로그에서 제외한다
    private static final List<String> NO_LOG_PREFIXES = List.of("/actuator", "/swagger-ui", "/v3/api-docs");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (shouldLog(request.getRequestURI())) {
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                String query = request.getQueryString();
                log.info("{} {}{} -> {} ({}ms)", request.getMethod(), request.getRequestURI(),
                        query == null ? "" : "?" + query, response.getStatus(), elapsedMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(String headerValue) {
        if (headerValue != null && VALID_REQUEST_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    private boolean shouldLog(String uri) {
        return NO_LOG_PREFIXES.stream().noneMatch(uri::startsWith);
    }
}

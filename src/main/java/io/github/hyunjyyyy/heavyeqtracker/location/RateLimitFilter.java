package io.github.hyunjyyyy.heavyeqtracker.location;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_SECOND = 10;

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/v1/locations".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String equipmentId = request.getHeader("X-Equipment-Id");
        if (equipmentId == null) {
            chain.doFilter(request, response);   // 헤더 없음은 Controller가 400으로 처리
            return;
        }

        long count = limiter.hit(equipmentId);
        if (count > LIMIT_PER_SECOND) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "equipment " + equipmentId + " exceeded " + LIMIT_PER_SECOND + " requests/second");
            problem.setInstance(URI.create(request.getRequestURI()));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "1");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(problem));
            return;
        }

        chain.doFilter(request, response);
    }
}

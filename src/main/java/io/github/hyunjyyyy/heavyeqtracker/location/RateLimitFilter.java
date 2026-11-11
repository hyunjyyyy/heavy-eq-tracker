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

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties props;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper, RateLimitProperties props) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.props = props;
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
        int limit = props.perSecond();
        if (count > limit) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "equipment " + equipmentId + " exceeded " + limit + " requests/second");
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

package io.github.hyunjyyyy.heavyeqtracker.site;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/* 경로의 {siteId}(본사 경로는 root)로 현재 현장을 정한다. 컨트롤러가 정해진 뒤 돌아 경로 변수가 꺼내져 있고,
   여기서 던진 예외는 @RestControllerAdvice가 받는다. */
@Component
public class SiteContextInterceptor implements HandlerInterceptor {

    private static final String SCOPE = SiteContextInterceptor.class.getName() + ".scope";
    static final String HQ_PREFIX = "/api/v1/hq/";

    private final SiteRepository sites;

    public SiteContextInterceptor(SiteRepository sites) {
        this.sites = sites;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long siteId = siteIdOf(request);
        if (siteId != SiteContext.ROOT && !sites.existsById(siteId)) {
            throw new SiteNotFoundException(siteId);
        }
        request.setAttribute(SCOPE, SiteContext.enter(siteId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (request.getAttribute(SCOPE) instanceof SiteContext.Scope scope) {
            scope.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static long siteIdOf(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(HQ_PREFIX)) {
            return SiteContext.ROOT;
        }
        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return Long.parseLong(vars.get("siteId"));
    }
}

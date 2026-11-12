package io.github.hyunjyyyy.heavyeqtracker.site;

import java.util.function.Supplier;

/* 지금 처리 중인 현장. Hibernate @TenantId가 세션을 열 때 읽는다.
   ★ 비어 있으면 현장 1로 답하는 건 임시다(#30 6단계에서 에러로 바꾼다). DB의 DEFAULT 1과 같이 없앤다. */
public final class SiteContext {

    public static final long ROOT = 0L;            // 본사·시스템 작업. 현장 조건 없이 전체
    private static final long TEMP_DEFAULT_SITE = 1L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private SiteContext() {
    }

    public static long current() {
        Long site = CURRENT.get();
        return site != null ? site : TEMP_DEFAULT_SITE;
    }

    /* 끝나면 이전 값으로 되돌린다. 안 지우면 이 스레드를 다시 쓰는 다음 요청·메시지가 이 현장으로 처리된다. */
    public static Scope enter(long siteId) {
        Long previous = CURRENT.get();
        CURRENT.set(siteId);
        return new Scope(previous);
    }

    public static <T> T call(long siteId, Supplier<T> work) {
        try (Scope ignored = enter(siteId)) {
            return work.get();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Long previous;

        private Scope(Long previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(long siteId, Runnable work) {
        call(siteId, () -> {
            work.run();
            return null;
        });
    }

    public static void runAsRoot(Runnable work) {
        run(ROOT, work);
    }
}

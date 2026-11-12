package io.github.hyunjyyyy.heavyeqtracker.site;

import java.util.function.Supplier;

/* 지금 처리 중인 현장. Hibernate @TenantId가 세션을 열 때 읽는다.
   정하지 않으면 NONE: 현장 표는 아무것도 안 보이고 저장은 FK로 실패한다(#30 결정 6). */
public final class SiteContext {

    public static final long ROOT = 0L;            // 본사·시스템 작업. 현장 조건 없이 전체
    public static final long NONE = -1L;           // 정하지 않음. 에러 대신 닫힌 쪽으로 실패

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private SiteContext() {
    }

    public static long current() {
        Long site = CURRENT.get();
        return site != null ? site : NONE;
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

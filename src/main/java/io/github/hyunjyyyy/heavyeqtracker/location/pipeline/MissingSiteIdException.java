package io.github.hyunjyyyy.heavyeqtracker.location.pipeline;

public class MissingSiteIdException extends RuntimeException {
    public MissingSiteIdException(String key) {
        super("location message without siteId: key=" + key);
    }
}

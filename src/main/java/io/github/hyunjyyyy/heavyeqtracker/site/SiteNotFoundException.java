package io.github.hyunjyyyy.heavyeqtracker.site;

public class SiteNotFoundException extends RuntimeException {
    public SiteNotFoundException(long siteId) {
        super("site " + siteId + " not found");
    }
}

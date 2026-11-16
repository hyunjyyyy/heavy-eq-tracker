package io.github.hyunjyyyy.heavyeqtracker.site;

import io.github.hyunjyyyy.heavyeqtracker.common.NotFoundException;

public class SiteNotFoundException extends NotFoundException {
    public SiteNotFoundException(long siteId) {
        super("site " + siteId + " not found");
    }
}

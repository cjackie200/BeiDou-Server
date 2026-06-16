package org.gms.util;

import java.util.Locale;

public final class SuspiciousRequestUtil {
    private SuspiciousRequestUtil() {
    }

    public static boolean isRepositoryProbe(String requestUri) {
        if (requestUri == null) {
            return false;
        }

        String uri = requestUri.toLowerCase(Locale.ROOT);
        return uri.equals("/.git") || uri.startsWith("/.git/") || uri.contains("/.git/");
    }
}

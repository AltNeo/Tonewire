package com.tonewire.tv;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Pins the foreground-service start-retry policy.
 *
 * <p>On API 31 {@code Context.startForegroundService} can return null without throwing when the
 * caller is not yet the top app. The TV screen therefore intermittently failed to start the
 * equalizer service, with only one log line to show for it. A retry fixed it, and this test
 * guards the policy so a refactor cannot quietly return to a single silent attempt.
 *
 * <p>These are source-level assertions on purpose: the behaviour depends on a platform callback
 * that cannot be reproduced in a plain JVM test, so the most valuable thing to lock down is that
 * the retry path still exists and stays bounded.
 */
public final class ServiceStartRetryTest {

    private static File serviceSource() {
        return locate(
                "src/main/java/com/tonewire/tv/TvEqualizerService.java",
                "android/tv/src/main/java/com/tonewire/tv/TvEqualizerService.java");
    }

    private static File activitySource() {
        return locate(
                "src/main/java/com/tonewire/tv/TvStatusActivity.java",
                "android/tv/src/main/java/com/tonewire/tv/TvStatusActivity.java");
    }

    private static File locate(String... candidates) {
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile()) return file;
        }
        throw new AssertionError("Could not locate source file among the known paths");
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new AssertionError("Could not read " + file, failure);
        }
    }

    @Test public void nullStartSchedulesABoundedRetry() {
        String service = read(serviceSource());
        // Assert the call site, not merely that a method with this name exists: a retry helper
        // that is never invoked would pass a loose check while the bug returned.
        assertTrue("requestStart must invoke the retry after a failed synchronous start",
                service.matches("(?s).*if \\(tryStartForeground\\(appContext, entryPoint\\)\\) return true;[\\s\\S]{0,200}?scheduleStartRetry\\(appContext.*"));
        assertTrue("retry count must be defined", service.contains("MAX_START_ATTEMPTS"));
        assertTrue("retry loop must be bounded",
                service.matches("(?s).*next\\s*<\\s*MAX_START_ATTEMPTS.*"));
    }

    @Test public void retryCountAllowsMoreThanOneAttemptButStaysBounded() {
        String service = read(serviceSource());
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("MAX_START_ATTEMPTS\\s*=\\s*(\\d+)").matcher(service);
        assertTrue("retry count must be a literal constant", matcher.find());
        int attempts = Integer.parseInt(matcher.group(1));
        assertTrue("more than one attempt is needed to tolerate the platform behaviour", attempts >= 3);
        assertTrue("an unbounded retry loop would be its own bug", attempts <= 20);
    }

    @Test public void aNullComponentIsTreatedAsFailureRatherThanSuccess() {
        String service = read(serviceSource());
        // The single-try helper must still report false for a null component, so the caller
        // knows to retry instead of assuming the service is on its way.
        assertTrue("null component must be reported as a failed start",
                service.matches("(?s).*component == null[\\s\\S]{0,300}?return false;.*"));
    }

    @Test public void activityStartsOnWindowFocusAndCancelsPendingRetries() {
        String activity = read(activitySource());
        assertTrue("activity must start the service when it gains focus",
                activity.contains("onWindowFocusChanged"));
        assertTrue("activity must check current focus rather than relying on one callback",
                activity.contains("hasWindowFocus()"));
        assertFalse("pending retries must be cancelled on pause",
                !activity.contains("removeCallbacks(startRetry)"));
    }
}

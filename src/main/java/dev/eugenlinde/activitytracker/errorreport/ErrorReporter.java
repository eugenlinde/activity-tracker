package dev.eugenlinde.activitytracker.errorreport;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ErrorReporter {

    static final String LABEL = "prod-error";
    private static final String APP_PACKAGE = "dev.eugenlinde.activitytracker";
    private static final int MAX_FRAMES = 30;
    private static final int MAX_CAUSES = 10;
    private static final int QUEUE_SIZE = 10;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final Pattern REPO = Pattern.compile("([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)");
    private static final Pattern HTTP_METHOD = Pattern.compile("[A-Z]{1,10}");
    private static final String CLOSING = "Find the cause, fix it, and add a test that reproduces it. "
            + "The error's message is not shown here on purpose; it is in the server's log.";

    private final RestClient restClient;
    private final ErrorReportProperties properties;
    private final Clock clock;
    private final Deque<Instant> opened = new ArrayDeque<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_SIZE),
            runnable -> {
                Thread thread = new Thread(runnable, "error-reporter");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public ErrorReporter(
            @Qualifier("errorReportRestClient") RestClient restClient,
            ErrorReportProperties properties,
            Clock clock
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    public void report(Throwable throwable, String httpMethod, String routePattern) {
        try {
            if (!properties.isEnabled()) {
                return;
            }
            executor.execute(() -> reportNow(throwable, httpMethod, routePattern));
        } catch (RejectedExecutionException rejected) {
            log.warn("Error report dropped: queue is full");
        } catch (Exception failure) {
            log.warn("Error report failed: {}", failure.getClass().getName());
        }
    }

    public void reportBrowser(String name, List<String> frames, String route) {
        try {
            if (!properties.isEnabled()) {
                return;
            }
            executor.execute(() -> reportBrowserNow(name, frames, route));
        } catch (RejectedExecutionException rejected) {
            log.warn("Error report dropped: queue is full");
        } catch (Exception failure) {
            log.warn("Error report failed: {}", failure.getClass().getName());
        }
    }

    public void reportNow(Throwable throwable, String httpMethod, String routePattern) {
        openOnce(now -> {
            String fingerprint = fingerprint(throwable);
            String method = httpMethod != null && HTTP_METHOD.matcher(httpMethod).matches() ? httpMethod : "unknown";
            String route = routePattern != null && !routePattern.isBlank() ? routePattern : "unknown";
            return new IssueDraft(fingerprint, title(throwable), body(throwable, method, route, now, fingerprint));
        });
    }

    public void reportBrowserNow(String name, List<String> frames, String route) {
        openOnce(now -> {
            String fingerprint = browserFingerprint(name, frames, route);
            return new IssueDraft(fingerprint, browserTitle(name, frames),
                    browserBody(name, frames, route, now, fingerprint));
        });
    }

    private record IssueDraft(String fingerprint, String title, String body) {
    }

    private void openOnce(Function<Instant, IssueDraft> drafter) {
        try {
            if (!properties.isEnabled()) {
                return;
            }
            RepoRef repo = parseRepo(properties.getGithubRepo());
            if (repo == null) {
                log.warn("Error report skipped: error-report.github-repo is not owner/name");
                return;
            }
            Instant now = clock.instant();
            if (limitReached(now)) {
                log.warn("Error report skipped: limit of {} per hour reached", properties.getMaxPerHour());
                return;
            }

            IssueDraft draft = drafter.apply(now);
            if (alreadyOpen(repo, draft.fingerprint())) {
                return;
            }

            Map<String, Object> issue = Map.of(
                    "title", draft.title(),
                    "body", draft.body(),
                    "labels", List.of(LABEL)
            );

            recordOpened(now);
            restClient.post()
                    .uri("/repos/{owner}/{repo}/issues", repo.owner(), repo.name())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(issue)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception failure) {
            log.warn("Error report failed: {}", failure.getClass().getName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private boolean alreadyOpen(RepoRef repo, String fingerprint) {
        List<Map<String, Object>> issues = restClient.get()
                .uri("/repos/{owner}/{repo}/issues?state=open&labels=" + LABEL + "&per_page=100",
                        repo.owner(), repo.name())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
        if (issues == null) {
            return false;
        }
        String marker = "Fingerprint: " + fingerprint;
        return issues.stream()
                .map(issue -> issue.get("body"))
                .anyMatch(text -> text instanceof String string && string.contains(marker));
    }

    private synchronized boolean limitReached(Instant now) {
        Instant boundary = now.minus(WINDOW);
        while (!opened.isEmpty() && !opened.peekFirst().isAfter(boundary)) {
            opened.pollFirst();
        }
        return opened.size() >= properties.getMaxPerHour();
    }

    private synchronized void recordOpened(Instant now) {
        opened.addLast(now);
    }

    private static RepoRef parseRepo(String value) {
        java.util.regex.Matcher matcher = REPO.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new RepoRef(matcher.group(1), matcher.group(2));
    }

    private record RepoRef(String owner, String name) {
    }

    static String fingerprint(Throwable throwable) {
        String input = throwable.getClass().getName() + "|" + placeQualified(throwable);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String browserFingerprint(String name, List<String> frames, String route) {
        String input = "browser|" + name + "|" + frames.get(0) + "|" + route;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String browserTitle(String name, List<String> frames) {
        return "Prod error (browser): " + name + " at " + frames.get(0);
    }

    static String browserBody(String name, List<String> frames, String route, Instant now, String fingerprint) {
        StringBuilder text = new StringBuilder();
        text.append("The browser hit an error nobody caught at ").append(now)
                .append(". This report came from the frontend, not from the backend.\n\n");
        text.append("Page: ").append(route).append("\n\n");
        text.append("Error: ").append(name).append("\n\n");
        text.append("```\n");
        for (String frame : frames) {
            text.append(frame).append('\n');
        }
        text.append("```\n\n");
        text.append("The frames are file, line and column in the built bundle, not in the source files.\n\n");
        text.append("Fingerprint: ").append(fingerprint).append("\n\n");
        text.append("Find the cause, fix it, and add a test that reproduces it. "
                + "The error's message is not shown here on purpose; the browser never sends it.");
        return text.toString();
    }

    static String title(Throwable throwable) {
        return "Prod error: " + simpleName(throwable) + " at " + placeSimple(throwable);
    }

    static String body(Throwable throwable, String method, String route, Instant now, String fingerprint) {
        StringBuilder text = new StringBuilder();
        text.append("The backend answered 500 for an unexpected error at ").append(now).append(".\n\n");
        text.append("Request: ").append(method).append(' ').append(route).append("\n\n");
        text.append("Exception: ").append(throwable.getClass().getName()).append('\n');
        Throwable cause = throwable.getCause();
        int depth = 0;
        while (cause != null && cause != throwable && depth < MAX_CAUSES) {
            text.append("Caused by: ").append(cause.getClass().getName()).append('\n');
            cause = cause.getCause();
            depth++;
        }
        text.append("\n```\n");
        StackTraceElement[] frames = throwable.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, MAX_FRAMES); i++) {
            StackTraceElement frame = frames[i];
            text.append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber()).append('\n');
        }
        text.append("```\n\n");
        text.append("Fingerprint: ").append(fingerprint).append("\n\n");
        text.append(CLOSING);
        return text.toString();
    }

    private static StackTraceElement place(Throwable throwable) {
        StackTraceElement[] frames = throwable.getStackTrace();
        if (frames.length == 0) {
            return null;
        }
        for (StackTraceElement frame : frames) {
            if (frame.getClassName().startsWith(APP_PACKAGE)) {
                return frame;
            }
        }
        return frames[0];
    }

    private static String placeQualified(Throwable throwable) {
        StackTraceElement frame = place(throwable);
        return frame == null ? "unknown" : frame.getClassName() + "." + frame.getMethodName();
    }

    private static String placeSimple(Throwable throwable) {
        StackTraceElement frame = place(throwable);
        if (frame == null) {
            return "unknown";
        }
        String className = frame.getClassName();
        return className.substring(className.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }

    private static String simpleName(Throwable throwable) {
        String name = throwable.getClass().getSimpleName();
        return name.isEmpty() ? throwable.getClass().getName() : name;
    }
}

package com.company.sage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Utility for dynamically resolving Git commit hash in a fail-soft manner.
 */
public final class GitUtil {

    private static final Logger log = LoggerFactory.getLogger(GitUtil.class);
    private static final String CACHED_COMMIT = resolveCommit();

    private GitUtil() {}

    public static String getCommitHash() {
        return CACHED_COMMIT;
    }

    private static String resolveCommit() {
        String envCommit = System.getenv("GIT_COMMIT");
        if (envCommit != null && !envCommit.isBlank()) {
            return envCommit.trim();
        }
        String gitOutput = runCommand("git", "rev-parse", "--short", "HEAD");
        return (gitOutput != null && !gitOutput.isBlank()) ? gitOutput.trim() : "unknown";
    }

    private static String runCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line;
            }
        } catch (Exception e) {
            log.debug("Fail-soft: Could not execute git command {}: {}", command, e.getMessage());
            return null;
        }
    }
}

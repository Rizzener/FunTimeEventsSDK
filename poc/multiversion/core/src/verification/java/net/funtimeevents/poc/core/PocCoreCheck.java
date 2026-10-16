package net.funtimeevents.poc.core;

/** Dependency-free contract checks, executed by the Gradle check task. */
public final class PocCoreCheck {
    public static void main(String[] args) {
        for (String invalid : new String[] { null, "", " " }) {
            try {
                new PocCore(invalid);
                throw new AssertionError("Invalid configuration accepted");
            } catch (IllegalArgumentException expected) {
                // Expected validation result, not an ignored runtime failure.
            }
        }
        if (!new PocCore("check").describe("value").equals("core[check]:value")) {
            throw new AssertionError("Core formatting contract failed");
        }
        System.out.println("[FTE-POC] CORE_PASS java=" + Runtime.version().feature());
    }
}

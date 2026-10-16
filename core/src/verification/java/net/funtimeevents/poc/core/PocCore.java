package net.funtimeevents.poc.core;

/** No dependency on Minecraft, Fabric, logging, or networking. */
public final class PocCore {
    private final String userAgent;

    public PocCore(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent is required");
        }
        this.userAgent = userAgent;
    }

    public String describe(String gameText) {
        return "core[" + userAgent + "]:" + gameText;
    }
}

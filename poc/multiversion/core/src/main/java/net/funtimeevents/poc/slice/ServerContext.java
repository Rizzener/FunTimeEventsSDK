package net.funtimeevents.poc.slice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Game state is owned by the client thread; snapshots are published atomically. */
public final class ServerContext {
    private static final Pattern FUN_TIME_PATTERN = Pattern.compile(
            "^(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*)funtime\\.(su|sh|me|store|network|wiki)$"
    );
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("Анархия-(\\d+)");

    public record State(int serverId, String serverIp, boolean onFuntime) {}
    private static final State EMPTY = new State(-1, null, false);
    private volatile State state = EMPTY;

    public State state() { return state; }
    public boolean isOnFuntime() { return state.onFuntime(); }
    public String getServerType() { return "anarchy"; }

    public int getServerId() {
        State current = state;
        if (!current.onFuntime()) {
            throw new IllegalStateException("server_id requested but not on FunTime. Check isOnFuntime() first.");
        }
        return current.serverId();
    }

    public void refresh(ServerAccess access) {
        String ip = access.serverAddress();
        boolean funtime = ip != null && FUN_TIME_PATTERN.matcher(ip).matches();
        int id = -1;
        if (funtime) {
            String title = access.sidebarTitle();
            if (title != null) {
                Matcher matcher = SERVER_ID_PATTERN.matcher(title);
                if (matcher.find()) id = Integer.parseInt(matcher.group(1));
            }
        }
        State old = state;
        if (old.serverId() != id || old.onFuntime() != funtime || !java.util.Objects.equals(old.serverIp(), ip)) {
            state = new State(id, ip, funtime);
        }
    }

    public void reset() { state = EMPTY; }
}

package net.funtimeevents.poc.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.funtimeevents.poc.slice.BanSliceRuntime;
import net.funtimeevents.poc.slice.IncomingMessage;
import net.funtimeevents.poc.slice.ServerAccess;
import net.funtimeevents.util.GsonHolder;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class BanSliceCheck {
    private static final class Access implements ServerAccess {
        volatile String host;
        volatile String title;
        public String serverAddress() { return host; }
        public String sidebarTitle() { return title; }
    }

    public static void main(String[] args) {
        JsonArray sessions = JsonParser.parseReader(new InputStreamReader(
            java.util.Objects.requireNonNull(BanSliceCheck.class.getResourceAsStream("/phase2/ban-sessions.json")),
            StandardCharsets.UTF_8)).getAsJsonArray();
        Access access = new Access();
        JsonArray actual = new JsonArray();
        JsonArray expected = new JsonArray();
        var runtime = new BanSliceRuntime(access, payload -> actual.add(GsonHolder.INSTANCE.toJsonTree(payload)));
        int count = 0;
        for (var element : sessions) {
            var session = element.getAsJsonObject();
            access.host = session.get("host").getAsString();
            access.title = null;
            runtime.joined();
            runtime.joined();
            for (var item : session.getAsJsonArray("actions")) {
                var action = item.getAsJsonObject();
                if (action.get("kind").getAsString().equals("sidebar")) {
                    access.title = action.get("title").getAsString();
                    runtime.tick();
                } else {
                    runtime.message(new IncomingMessage() {
                        public String rawText() { return action.get("text").getAsString(); }
                        public String plainText() { return action.get("text").getAsString(); }
                        public String hoverText() { return action.has("hover") ? action.get("hover").getAsString() : ""; }
                    });
                    if (!action.get("payload").isJsonNull()) expected.add(action.get("payload"));
                }
            }
            runtime.disconnected();
            runtime.disconnected();
            if (runtime.state().serverId() != -1 || runtime.state().serverIp() != null || runtime.state().onFuntime()) {
                throw new AssertionError("Stale state after disconnect");
            }
            count++;
        }
        if (!actual.equals(expected) || runtime.joins() != count || runtime.disconnects() != count) {
            throw new AssertionError("Payload/lifecycle mismatch: " + actual + " != " + expected);
        }
        System.out.println("[PHASE2-CORE] PASS sessions=" + count + " payloads=" + actual.size());
    }
}

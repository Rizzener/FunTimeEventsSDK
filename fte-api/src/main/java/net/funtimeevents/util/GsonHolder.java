package net.funtimeevents.util;

import com.google.gson.Gson;

public final class GsonHolder {

    public static final Gson INSTANCE = new Gson();

    private GsonHolder() {
    }
}

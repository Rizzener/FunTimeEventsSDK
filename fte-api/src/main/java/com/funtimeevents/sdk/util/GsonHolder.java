package com.funtimeevents.sdk.util;

import com.google.gson.Gson;

public final class GsonHolder {

    public static final Gson INSTANCE = new Gson();

    private GsonHolder() {
    }
}

package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Request: captcha image data sent to the backend.
 */
public final class CaptchaPayload {

    @SerializedName("base64")
    private final String base64;

    public CaptchaPayload(String base64) {
        this.base64 = base64;
    }

    public String base64() { return base64; }
}

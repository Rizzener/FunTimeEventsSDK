package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CaptchaResponse {

    @SerializedName("solved")
    private boolean solved;

    @SerializedName("text")
    private String text;

    @SerializedName("overall_percent")
    private Double overallPercent;

    @SerializedName("results")
    private List<PercentResult> results;

    public boolean solved() { return solved; }
    public String text() { return text; }
    public Double overallPercent() { return overallPercent; }
    public List<PercentResult> results() { return results; }

    public static final class PercentResult {
        @SerializedName("number")
        private String number;

        @SerializedName("confidence_percent")
        private double confidencePercent;

        public String number() { return number; }
        public double confidencePercent() { return confidencePercent; }
    }
}

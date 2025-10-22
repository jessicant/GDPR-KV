package com.example.gdprkv.service;

import lombok.Getter;

public class GdprKvException extends RuntimeException {

    public enum Code {
        INVALID_PURPOSE,
        UNKNOWN
    }

    @Getter
    private final Code code;

    private GdprKvException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public static GdprKvException invalidPurpose(String purpose) {
        return new GdprKvException(Code.INVALID_PURPOSE,
                "Purpose " + purpose + " is not configured");
    }
}

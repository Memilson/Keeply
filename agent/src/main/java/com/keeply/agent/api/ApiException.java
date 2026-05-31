package com.keeply.agent.api;

public final class ApiException extends RuntimeException {
    private final int statusCode;
    private final String error;

    public ApiException(int statusCode, String message, String error) {
        super(message);
        this.statusCode = statusCode;
        this.error = error;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ApiException{statusCode=" + statusCode + ", message='" + getMessage() + "', error='" + error + "'}";
    }
}

package com.example.vintage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatResponse {
    private String response;
    private boolean success;
    private String error;

    public ChatResponse() {}

    public ChatResponse(String response, boolean success) {
        this.response = response;
        this.success = success;
    }

    public ChatResponse(String error) {
        this.error = error;
        this.success = false;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

package com.example.audio2text.model;

import com.google.gson.annotations.SerializedName;

public class Task {
    @SerializedName("operation")
    private String operation;
    @SerializedName("input")
    private String input;

    public Task(String operation, String input) {
        this.operation = operation;
        this.input = input;
    }
}
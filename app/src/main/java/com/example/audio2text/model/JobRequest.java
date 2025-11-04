package com.example.audio2text.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class JobRequest {
    @SerializedName("tasks")
    private Map<String, Object> tasks;

    public JobRequest(Map<String, Object> tasks) {
        this.tasks = tasks;
    }
}
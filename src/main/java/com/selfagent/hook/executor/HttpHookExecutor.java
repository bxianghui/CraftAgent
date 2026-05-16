package com.selfagent.hook.executor;

import com.selfagent.hook.HookConfig;
import com.selfagent.hook.HookInput;
import com.selfagent.hook.HookOutput;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public class HttpHookExecutor {
    private static final MediaType JSON = MediaType.get("application/json");

    public HookOutput execute(HookConfig.HookEntry entry, HookInput input) {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(entry.timeout, TimeUnit.SECONDS)
            .readTimeout(entry.timeout, TimeUnit.SECONDS)
            .build();
        try {
            Request req = new Request.Builder()
                .url(entry.url)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(input.toJson(), JSON))
                .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    System.err.println("[Hook] HTTP " + resp.code() + " from " + entry.url);
                    return HookOutput.allow();
                }
                String body = resp.body().string().trim();
                if (body.isEmpty()) return HookOutput.allow();
                return CommandHookExecutor.parseOutput(body);
            }
        } catch (Exception e) {
            System.err.println("[Hook] HTTP error: " + e.getMessage());
            return HookOutput.allow();
        }
    }
}

package com.selfagent.hook;

import java.util.List;
import java.util.Map;

public class HookConfig {
    public Map<String, List<HookGroup>> hooks = new java.util.LinkedHashMap<>();

    public static class HookGroup {
        public String matcher = "*";
        public List<HookEntry> hooks = new java.util.ArrayList<>();
    }

    public static class HookEntry {
        public String type;       // command | http | prompt
        public String command;    // type=command
        public String url;        // type=http
        public String prompt;     // type=prompt
        public int timeout = 60;
        public boolean async = false;
    }
}

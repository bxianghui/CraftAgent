package com.selfagent.skill;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 监听 skill 目录的文件变化（add/change/delete），防抖 300ms 后触发 SkillManager.refresh()。
 * 使用 JDK 内置 WatchService，无额外依赖。
 */
public class SkillWatcher {
    private final SkillManager skillManager;
    private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "skill-watcher-debounce");
        t.setDaemon(true);
        return t;
    });
    private final Thread watchThread;
    private volatile boolean running = false;
    private ScheduledFuture<?> pendingReload;

    public SkillWatcher(SkillManager skillManager) {
        this.skillManager = skillManager;
        this.watchThread = new Thread(this::watchLoop, "skill-watcher");
        this.watchThread.setDaemon(true);
    }

    public void start() {
        running = true;
        watchThread.start();
    }

    public void stop() {
        running = false;
        watchThread.interrupt();
        debouncer.shutdown();
    }

    private void watchLoop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            registerIfExists(watcher, skillManager.getProjectSkillsDir());
            registerIfExists(watcher, skillManager.getGlobalSkillsDir());

            if (!hasRegistered(watcher)) return;

            while (running) {
                WatchKey key;
                try {
                    key = watcher.poll(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (key == null) continue;

                Path watchedDir = (Path) key.watchable();
                boolean needRefresh = false;
                boolean needPromptReload = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path name = (Path) event.context();
                    if (name == null) continue;
                    Path full = watchedDir.resolve(name);

                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(full)) {
                        // 新增 skill 目录：注册监听 + 触发 refresh
                        try {
                            full.register(watcher,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                        } catch (IOException ignored) {}
                        needRefresh = true;
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE && isSkillRootDir(watchedDir)) {
                        // skill 目录被删除：触发 refresh
                        needRefresh = true;
                    } else if (name.toString().equals("SKILL.md")) {
                        // 正文变更：只重新 parse，不重建列表
                        needPromptReload = true;
                    }
                }

                key.reset();

                if (needRefresh) {
                    scheduleRefresh();
                } else if (needPromptReload) {
                    schedulePromptReload(watchedDir);
                }
            }
        } catch (IOException e) {
            System.err.println("[Skill] Watcher error: " + e.getMessage());
        }
    }

    private boolean isSkillRootDir(Path dir) {
        Path project = skillManager.getProjectSkillsDir();
        Path global = skillManager.getGlobalSkillsDir();
        return (project != null && project.equals(dir)) || (global != null && global.equals(dir));
    }

    /** skill 增删：重建列表 + dirty，防抖 300ms */
    private synchronized void scheduleRefresh() {
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }
        pendingReload = debouncer.schedule(skillManager::refresh, 300, TimeUnit.MILLISECONDS);
    }

    /** 正文变更：只重新 parse，不动列表，不置 dirty，防抖 300ms */
    private synchronized void schedulePromptReload(Path skillDir) {
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }
        pendingReload = debouncer.schedule(() -> skillManager.reloadPrompt(skillDir), 300, TimeUnit.MILLISECONDS);
    }

    private void registerIfExists(WatchService watcher, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try {
            dir.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            // 同时监听子目录（skill 各自在子目录里）
            Files.list(dir).filter(Files::isDirectory).forEach(sub -> {
                try {
                    sub.register(watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            System.err.println("[Skill] Cannot watch " + dir + ": " + e.getMessage());
        }
    }

    private boolean hasRegistered(WatchService watcher) {
        // WatchService 没有直接查询已注册 key 的方法，通过 poll 测试
        return skillManager.getProjectSkillsDir() != null
            && Files.isDirectory(skillManager.getProjectSkillsDir())
            || skillManager.getGlobalSkillsDir() != null
            && Files.isDirectory(skillManager.getGlobalSkillsDir());
    }
}

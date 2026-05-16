---
name: 异常处理准则
description: coding-agent 项目的异常处理规范 — 非核心功能失败只打印警告跳过，不影响主流程
type: feedback
originSessionId: 7d24d62d-3e86-43d4-bde1-a2c13a1bdcee
---
非核心功能（MCP、插件、配置加载等）的异常处理规则：
- 失败时打印警告（System.err.println）提示用户
- 跳过当前失败项，继续执行其他功能
- 不向上抛异常，不影响主流程使用

**Why:** 用户明确要求：单个模块加载失败不能影响整体功能使用，可以跳过并给出提示。

**How to apply:** 所有非核心路径（MCP server 连接、插件加载、可选配置解析等）都用 try-catch(Exception) 包裹，catch 里只打印提示不重新抛出。核心路径（LLM provider 调用、用户输入处理）仍可正常抛出。

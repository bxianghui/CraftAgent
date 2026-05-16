# self-agent

基于 LLM 的命令行 AI 编程助手。支持工具调用、MCP 协议、Skill 系统、长期记忆、RAG 知识库、多 Agent 协同、沙箱安全隔离。

---

## 目录

1. [安装与启动](#安装与启动)
2. [配置文件](#配置文件)
3. [支持的模型 Provider](#支持的模型-provider)
4. [内置工具](#内置工具)
5. [斜杠命令](#斜杠命令)
6. [Skill 系统](#skill-系统)
7. [Memory 系统](#memory-系统)
8. [RAG 知识库](#rag-知识库)
9. [MCP 协议扩展](#mcp-协议扩展)
10. [多 Agent 协同](#多-agent-协同)
11. [Selfcheck 验证](#selfcheck-验证)
12. [沙箱安全](#沙箱安全)
13. [Hook 系统](#hook-系统)
14. [Learned Skills](#learned-skills)
15. [会话与历史](#会话与历史)
16. [日志与可观测性](#日志与可观测性)
17. [数据目录结构](#数据目录结构)

---

## 安装与启动

**前置要求**：Java 17+

```bash
bash install-dist.sh
```

安装脚本会：
- 检查 Java 版本是否满足要求
- 将 `self-agent` 命令复制到 `~/bin/`
- 生成默认配置文件 `~/.self-agent/config.yaml`
- 自动检测 `~/bin` 是否在 PATH，不在则追加到 `~/.zshrc`

安装后编辑配置文件，填入 API Key：

```bash
vim ~/.self-agent/config.yaml
```

### 启动方式

```bash
self-agent                          # 交互模式（REPL）
self-agent --model kimi             # 指定 provider 启动
self-agent --yes                    # 自动确认所有操作，跳过审批弹窗
self-agent run "帮我写一个排序算法"   # 单次执行后退出
self-agent --resume                 # 恢复上一次会话上下文
```

---

## 配置文件

主配置文件路径：`~/.self-agent/config.yaml`

```yaml
default_provider: anthropic       # 默认使用的 provider

providers:
  anthropic:
    api_key: "sk-ant-..."
    model: "claude-opus-4-5-20251001"

  openai:
    api_key: "sk-..."
    model: "gpt-4o"
    base_url: "https://api.openai.com"

  minimax:
    api_key: "..."
    model: "MiniMax-Text-01"
    base_url: "https://api.minimaxi.com/anthropic"

  ollama:
    base_url: "http://localhost:11434"
    model: "llama3"

  kimi:
    api_key: "..."
    model: "moonshot-v1-8k"
    base_url: "https://api.moonshot.cn/v1"

  deepseek:
    api_key: "..."
    model: "deepseek-chat"
    base_url: "https://api.deepseek.com"

context:
  max_token_ratio: 0.8            # 超过此比例触发历史压缩，默认 0.8
  keep_recent_turns: 20           # 压缩时保留最近 N 轮，默认 20
  memory_promote_interval: 20     # 每隔 N 轮触发一次长期记忆提炼

rag:
  enabled: true                   # RAG 全局开关
  embedding_provider: anthropic   # 用于生成向量的 provider（默认与 default_provider 相同）

log:
  timing_log: false               # 启动时是否开启耗时日志

sandbox:
  enabled: false                  # 沙箱安全隔离开关
  allow_network: true             # 是否允许网络访问
  allow_commands:                 # 白名单命令（支持通配符前缀）
    - "git *"
    - "mvn *"
  deny_write_paths:               # 禁止写入的路径
    - "/etc"
    - "/usr"

system_prompt: "你是一名 Java 专家"     # 自定义系统提示（与 system_prompt_file 二选一）
system_prompt_file: "/path/to/prompt.md"
```

---

## 支持的模型 Provider

| Provider | 协议 | 说明 |
|---|---|---|
| `anthropic` | Anthropic 原生 | Claude 系列，支持 thinking |
| `openai` | OpenAI | GPT 系列 |
| `minimax` | OpenAI 兼容 | MiniMax 系列 |
| `ollama` | OpenAI 兼容 | 本地模型，无需 API Key |
| `kimi` | OpenAI 兼容 | Moonshot Kimi 系列 |
| `deepseek` | OpenAI 兼容 | DeepSeek 系列 |

所有 provider 均支持流式输出和工具调用。

---

## 内置工具

模型在对话中自主调用以下工具完成任务：

### bash
执行 shell 命令，返回 stdout/stderr。

- 参数：`cmd`（必填）
- 输出上限：10000 字符（超出自动截断）
- 永久拦截：`rm -rf`、`mkfs.`、`dd if=`、`shred`、fork bomb、危险 kill 命令等

### read_file
读取文件内容，带行号。

- 参数：`path`（必填）、`start_line`（可选）、`end_line`（可选）
- 超过 500 行时建议指定范围

### write_file
写入或完全覆盖文件。

- 参数：`path`（必填）、`content`（必填）
- 文件不存在时自动创建

### edit_file
精准替换文件中的指定字符串。

- 参数：`path`（必填）、`old_string`（必填）、`new_string`（必填）
- 大小写敏感，需完全匹配（含空格、缩进），替换所有匹配项

### list_files
列出目录结构，支持 glob 过滤。

- 参数：`path`（必填）、`glob`（可选，如 `**/*.java`）
- 输出上限：200 条，自动跳过无权限目录

### search_code
在文件内容中正则搜索，返回匹配行和位置。

- 参数：`pattern`（必填，正则）、`path`（目录）、`glob`（文件过滤）
- 输出上限：500 条

### web_fetch
获取 URL 网页内容（去除 HTML 标签）。

- 参数：`url`（必填）
- 返回上限：8000 字符

---

## 斜杠命令

### 基础

| 命令 | 说明 |
|---|---|
| `/help` | 显示所有可用命令 |
| `/clear` | 清空当前对话上下文（不影响历史记录） |

### Provider 切换

| 命令 | 说明 |
|---|---|
| `/model <name>` | 运行时切换 provider，如 `/model kimi` |
| `/models` | 列出所有已配置 provider，标注当前使用的 |

### 工具

| 命令 | 说明 |
|---|---|
| `/tools` | 列出所有可用工具（内置 + MCP 工具） |

### 会话与历史

| 命令 | 说明 |
|---|---|
| `/sessions` | 列出最近 20 个会话 ID |
| `/history` | 列出最近 10 个会话 |
| `/history last` | 查看最近一次会话的完整调用链 |
| `/history <sessionId>` | 查看指定会话的调用链（工具调用、模型回复、耗时） |

### MCP 服务器

| 命令 | 说明 |
|---|---|
| `/mcp` | 列出所有已连接的 MCP server 及工具数 |
| `/mcp <name>` | 展开指定 server 的工具列表 |
| `/mcp enable <name>` | 启用并连接指定 server（持久化） |
| `/mcp disable <name>` | 断开并禁用指定 server（持久化） |
| `/mcp refresh` | 重新加载 config.mcp.json，连接新增 server |

### Skill

| 命令 | 说明 |
|---|---|
| `/skills` | 列出所有已加载的 skill（含来源标记） |
| `/skill <name>` | 激活指定 skill，将其正文注入对话上下文 |
| `/skill refresh` | 热加载磁盘上的 skill 变更（无需重启） |
| `/<skill-name>` | 直接激活同名 skill |

### Memory

| 命令 | 说明 |
|---|---|
| `/memory` | 查看当前 session 的临时记忆条目 |
| `/memory long` | 查看长期持久化记忆 |
| `/remember <内容>` | 手动保存一条记忆，LLM 自动生成名称、摘要、类型 |
| `/forget <关键词>` | 删除所有名称匹配关键词的记忆条目 |

### RAG 知识库

| 命令 | 说明 |
|---|---|
| `/import <path>` | 导入本地文件（支持 `.md`、`.txt`、`.pdf`） |
| `/import <url>` | 导入网页内容 |
| `/rag` | 查看 RAG 状态（开关、已索引块数、已导入文档列表） |
| `/rag on` | 启用 RAG（持久化） |
| `/rag off` | 禁用 RAG（索引保留，持久化） |

### 多 Agent

| 命令 | 说明 |
|---|---|
| `/agent <task>` | 以 `general-purpose` 类型启动子 agent 执行任务 |
| `/agent <type> <task>` | 以指定类型启动子 agent，如 `/agent explore 找出所有 TODO` |

### Selfcheck 验证

| 命令 | 说明 |
|---|---|
| `/verify` | 以最近对话上下文启动验证 agent，输出 PASS/FAIL/PARTIAL |
| `/verify <prompt>` | 带自定义验证指令启动验证 agent |

### 沙箱

| 命令 | 说明 |
|---|---|
| `/sandbox` | 查看沙箱状态（开关、运行时类型、白名单、禁止路径） |
| `/sandbox on` | 启用沙箱（即时生效） |
| `/sandbox off` | 禁用沙箱 |

### 调试与可观测性

| 命令 | 说明 |
|---|---|
| `/timing` | 查看耗时日志当前状态 |
| `/timing on` | 开启各阶段耗时日志（持久化） |
| `/timing off` | 关闭耗时日志（持久化） |
| `/thinking` | 查看 thinking 显示当前状态 |
| `/thinking on` | 开启模型 thinking 实时显示（灰色前缀，不加入上下文，持久化） |
| `/thinking off` | 关闭 thinking 显示（持久化） |

---

## Skill 系统

Skill 是预定义的角色指令，激活后注入到对话上下文，引导模型以特定方式工作。

### 目录结构

```
~/.self-agent/skills/<skill-name>/SKILL.md           # 全局 skill
<工作目录>/.self-agent/skills/<skill-name>/SKILL.md  # 项目级 skill（优先级更高）
~/.self-agent/learned/<skill-name>/SKILL.md          # 自动提炼 skill（优先级最低）
```

### SKILL.md 格式

```markdown
---
name: my-skill
description: 一句话描述 skill 的用途
---

你是一名资深 Java 架构师，专注于...

当用户提出问题时，你需要：
1. ...
2. ...
```

Skill 正文中可使用 `$ARGUMENTS` 接收激活时的附加参数：

```bash
/my-skill 帮我审查这段代码
# $ARGUMENTS 会被替换为「帮我审查这段代码」
```

### 热加载

修改 SKILL.md 后无需重启，文件变更自动检测（300ms 防抖）。也可手动执行 `/skill refresh`。

---

## Memory 系统

### Session 记忆（临时）

当前会话内有效，会话结束后清除。通过 `/remember` 手动写入，或模型自主记录。

```bash
/memory
/remember 我偏好使用 Stream API 而非 for 循环
```

### 长期记忆（持久化）

保存在 `~/.self-agent/memory/`，跨会话有效。每轮对话自动根据用户输入检索相关记忆注入 system prompt。会话结束时由 LLM 自动提炼新增/合并/删除条目（后台异步执行）。

记忆类型：

| 类型 | 说明 |
|---|---|
| `user` | 用户背景、偏好、技术栈 |
| `feedback` | 用户对模型行为的纠正或确认 |
| `project` | 项目目标、进度、关键决策 |
| `reference` | 外部系统、文档、链接 |
| `task` | 具体任务、待办事项 |

```bash
/memory long
/forget java    # 删除名称包含 "java" 的记忆条目
```

---

## RAG 知识库

将本地文档或网页导入本地向量索引，模型可通过 `search_docs` 工具检索。

```bash
/import ~/docs/architecture.md      # 导入 Markdown / TXT / PDF
/import https://example.com/docs    # 导入网页
/rag                                # 查看状态和已导入文档列表
/rag off                            # 禁用（索引保留）
```

导入流程：文本提取 → 分块 → 向量化 → 写入 HNSW 索引，持久化到 `~/.self-agent/rag/`，重启后自动加载。

指定 embedding provider（不设则默认使用 `default_provider`）：

```yaml
rag:
  embedding_provider: openai
```

---

## MCP 协议扩展

支持 MCP 2025-03-26 协议，可接入任意 MCP server 扩展工具集，支持 stdio 和 HTTP 两种传输方式。

配置文件 `~/.self-agent/config.mcp.json`：

```json
[
  {
    "name": "filesystem",
    "transport": "stdio",
    "command": ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    "enabled": true
  },
  {
    "name": "my-api",
    "transport": "http",
    "url": "http://localhost:3000/mcp",
    "enabled": true
  }
]
```

启动时并行连接所有 `enabled: true` 的 server，工具自动注册到工具列表。运行时管理：

```bash
/mcp                      # 列出已连接 server
/mcp filesystem           # 查看 server 工具列表
/mcp enable my-api        # 启用并连接
/mcp disable filesystem   # 禁用并断开
/mcp refresh              # 重新加载配置
```

---

## 多 Agent 协同

### 内置 Agent 类型

| 类型 | 可用工具 | 适用场景 |
|---|---|---|
| `general-purpose` | 全部工具 | 通用复杂任务 |
| `explore` | read_file, list_files, search_code, bash, web_fetch | 只读代码探索、问答 |
| `coder` | read_file, write_file, edit_file, bash, list_files, search_code | 代码实现 |
| `reviewer` | read_file, list_files, search_code, bash | 只读代码审查 |
| `verification` | read_file, list_files, search_code, bash | 验证实现正确性 |

```bash
/agent 找出所有未处理的 TODO 注释
/agent explore 入口类在哪里
/agent coder 实现一个 LRU 缓存
/agent reviewer 审查 ReactLoop.java 的错误处理
```

### 自定义 Agent

在 `~/.self-agent/agents/<name>.md` 或 `<工作目录>/.self-agent/agents/<name>.md` 中创建：

```markdown
---
name: my-agent
description: 专注于数据库相关任务的 agent
tools:
  - read_file
  - bash
  - search_code
model: deepseek    # 可选，不填则继承父 agent
max_turns: 10      # 可选，最大轮次
---

你是一名数据库专家，专注于 MySQL 性能优化...
```

项目级 agent 优先级高于全局。多个 agent 可通过 `run_in_background=true` 并行执行，主 agent 在当前轮次结束后统一收集结果。

---

## Selfcheck 验证

对实现完成后的代码进行对抗性验证。

```bash
/verify
/verify 重点检查边界值处理是否正确
```

验证 agent 依次执行：读取项目结构 → 运行构建 → 运行测试 → 执行对抗性探针（边界值、异常输入、回归检查）。

输出结果：
- `VERDICT: PASS` — 全部通过
- `VERDICT: FAIL` — 发现可复现问题，自动注入修复任务到主 agent
- `VERDICT: PARTIAL` — 环境限制（缺少工具、无法启动服务）

除手动触发外，工具调用累计 ≥ 3 次时模型会收到提示，也可由模型主动调用。

---

## 沙箱安全

### 三级决策

| 级别 | 触发条件 | 行为 |
|---|---|---|
| BLOCK | 匹配永久黑名单 | 永远拒绝，不受 sandbox 开关影响 |
| ALLOW | 匹配 `allow_commands` 白名单 | 直接执行 |
| REQUIRE_APPROVAL | 其他命令 | 弹出确认提示，用户输入 y/n |

**永久黑名单（不可绕过）**：`rm -rf`、`mkfs.`、`dd if=`、`shred`、fork bomb、`kill -9 1`、`kill -KILL 1`、`pkill -9 -u root`、`chmod -R 777 /`、`chown -R /`（排除 /tmp）、`mv /etc/passwd`、`mv /etc/shadow`、`truncate /etc/`、写入磁盘设备

OS 级隔离：macOS 使用 `sandbox-exec`（seatbelt），Linux 使用 `bwrap`，其他平台使用应用层拦截。

```yaml
sandbox:
  enabled: true
  allow_network: true
  allow_commands:
    - "git *"
    - "mvn *"
  deny_write_paths:
    - "/etc"
    - "/usr"
```

```bash
/sandbox on    # 启用（即时生效）
/sandbox       # 查看状态
```

`--yes` 只跳过 REQUIRE_APPROVAL，永远不绕过 BLOCK。

---

## Hook 系统

在特定事件发生时自动执行自定义逻辑。

配置文件 `~/.self-agent/config.hooks.json`：

```json
[
  {
    "event": "PreToolUse",
    "tool": "bash",
    "type": "command",
    "command": "echo '即将执行: $TOOL_INPUT'"
  },
  {
    "event": "PostToolUse",
    "tool": "write_file",
    "type": "http",
    "url": "http://localhost:8080/audit"
  },
  {
    "event": "UserPromptSubmit",
    "type": "prompt",
    "prompt": "在回答前，先确认用户的意图是否明确。"
  }
]
```

### 事件类型

| 事件 | 触发时机 |
|---|---|
| `PreToolUse` | 工具调用前 |
| `PostToolUse` | 工具调用后 |
| `UserPromptSubmit` | 用户提交消息后 |
| `SessionStart` | 会话启动时 |
| `SessionEnd` | 会话结束时 |

### 执行方式

| 类型 | 说明 |
|---|---|
| `command` | 执行 shell 命令 |
| `http` | 发送 HTTP POST 请求 |
| `prompt` | 将指令注入到模型上下文 |

### 阻断与修改

- 退出码 `2`：阻断工具执行
- 输出 `{"additionalContext": "..."}` ：向模型注入额外上下文
- 输出 `{"stopAgent": true}`：停止 agent 运行

---

## Learned Skills

会话结束时，满足以下条件自动提炼 skill 并保存到 `~/.self-agent/learned/`：

1. 本次会话工具调用次数 ≥ 3
2. 满足以下任一：
   - Selfcheck 验证通过（`VERDICT: PASS`）
   - 用户发送了确认词（「好了」「解决了」「谢谢」「done」「fixed」「works」等）

提炼流程：LLM 分析完整对话历史 → 判断是否有可复用解决流程 → 生成 skill 写入磁盘。

```bash
/skills    # 标注 [learned] 来源的即为自动提炼
```

优先级：项目级手动 skill > 全局手动 skill > learned skill。同名手动 skill 自动覆盖 learned skill。

---

## 会话与历史

每次会话自动记录到 `~/.self-agent/sessions/`：

- `history.jsonl`：事件流（用户输入、模型回复、工具调用、耗时）
- `requests.json`：完整 LLM 请求日志（含 tools schema，增量记录，实时落盘）

```bash
/sessions               # 列出最近 20 个会话 ID
/history last           # 查看最近一次会话详情
/history <sessionId>    # 查看指定会话
```

---

## 日志与可观测性

### 耗时日志

```bash
/timing on     # 开启各阶段耗时（stream、toolCalls、memorySearch 等）
/timing off    # 关闭
```

也可在 `config.yaml` 中设置启动默认值：

```yaml
log:
  timing_log: true
```

### Thinking 显示

适用于支持 thinking 的模型（如 Claude）：

```bash
/thinking on     # 流式显示 thinking 内容（灰色，不加入上下文）
/thinking off    # 关闭
```

### 崩溃日志

程序异常退出时自动追加写入 `~/.self-agent/crash.log`。

---

## 数据目录结构

```
~/.self-agent/
├── config.yaml              # LLM provider 配置
├── config.mcp.json          # MCP server 配置
├── config.hooks.json        # Hook 配置
├── crash.log                # 崩溃日志
├── timing.enabled           # 耗时日志开关（文件存在即开启）
├── thinking.enabled         # thinking 显示开关
├── sessions/                # 会话记录
│   └── <sessionId>/
│       ├── history.jsonl    # 事件流
│       └── requests.json    # LLM 请求日志
├── memory/                  # 长期记忆
│   ├── MEMORY.md            # 记忆索引
│   └── <name>.md            # 各条记忆文件
├── rag/                     # RAG 向量索引
│   ├── index.bin            # HNSW 索引
│   ├── chunks.json          # 文本块
│   └── config.json          # RAG 配置
├── plugins/                 # 自定义工具 JAR（动态加载）
├── skills/                  # 全局手动 skill
│   └── <skill-name>/SKILL.md
├── learned/                 # 自动提炼 skill
│   └── <skill-name>/SKILL.md
└── agents/                  # 全局自定义 agent
    └── <name>.md

<工作目录>/.self-agent/
├── skills/                  # 项目级 skill（优先级高于全局）
└── agents/                  # 项目级 agent
```

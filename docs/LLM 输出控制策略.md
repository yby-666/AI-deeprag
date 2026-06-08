# LLM 输出控制策略

> 以意图分类（IntentClassifier）为案例，阐述如何系统性地控制大语言模型的输出，使其在工程场景中稳定、可解析。

---

## 目录

1. [问题背景](#1-问题背景)
2. [提示词工程](#2-提示词工程)
3. [模型参数控制](#3-模型参数控制)
4. [应用层兜底策略](#4-应用层兜底策略)
5. [完整方案总览](#5-完整方案总览)
6. [最佳实践清单](#6-最佳实践清单)

---

## 1. 问题背景

在 RAG 系统中，意图分类是查询处理的第一步。我们需要将用户查询分入 `FACTUAL` / `PROCEDURAL` / `COMPARISON` / `CHITCHAT` 四类之一，后续根据分类结果走不同的检索-生成链路。

**核心挑战**：LLM 本质上是概率生成模型，即使提示词明确要求"只回复一个单词"，它也可能输出：

```
FACTUAL。因为用户询问的是具体的事实问题...
```

或者更糟——完全无法解析的内容。

**解决方案**：分层防御，从提示词、模型参数、应用层解析三个维度进行约束。

```
┌─────────────────────────────────────────────────┐
│              应用层兜底（最后一道防线）              │
│  ┌─────────────────────────────────────────────┐ │
│  │         模型参数控制（温度=0）                  │ │
│  │  ┌─────────────────────────────────────────┐ │ │
│  │  │       提示词工程（第一道防线）              │ │ │
│  │  │  · 角色设定  · 边界约束  · Few-shot      │ │ │
│  │  └─────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

---

## 2. 提示词工程

提示词是与 LLM 交互的唯一接口。写好提示词，是从源头控制输出的关键。

### 2.1 设定 LLM 角色

为 LLM 赋予明确角色，能将其行为锚定在特定领域，减少无关输出。

**原则**：
- 一句话说清楚"你是谁"
- 角色与任务紧密相关
- 避免过于宽泛的角色描述（如"你是一个AI助手"）

**示例**（IntentClassifier）：

```text
你是一个查询意图分类器。
```

仅 9 个字，但清晰界定了 LLM 在这个对话中的身份——一个执行特定分类任务的工具，而非通用聊天助手。这种"工具化"的角色设定有助于抑制 LLM 进行额外解释或闲聊的倾向。

**反面示例**：

```text
你是一个智能的、乐于助人的AI助手，擅长理解和分析用户意图...
```

这种描述暗示 LLM 可以进行开放式对话，反而增加了输出不可控的风险。

### 2.2 规范 LLM 边界

角色设定之后，需要用精确的指令约束 LLM 的行为边界——什么可以做，什么不可以做。

**核心要素**：

| 要素 | 说明 | 示例 |
|------|------|------|
| **输出格式** | 明确输出的结构和内容 | "只回复意图类别名称" |
| **禁止行为** | 明确禁止什么 | "不要回复其他内容" |
| **选项集合** | 限定可选值范围 | "FACTUAL / PROCEDURAL / COMPARISON / CHITCHAT" |
| **处理未知** | 对无法分类的输入给出指令 | （可选）"若无法分类，回复 FACTUAL" |

**示例**（IntentClassifier）：

```text
请将以下用户查询分类为四种意图之一：
- FACTUAL：事实查询，寻找具体的事实、定义、数据等
- PROCEDURAL：步骤查询，询问如何完成某件事的操作流程
- COMPARISON：对比查询，比较两个或多个事物的异同
- CHITCHAT：闲聊，不需要检索的日常对话

请只回复意图类别名称（FACTUAL / PROCEDURAL / COMPARISON / CHITCHAT），不要回复其他内容。
```

**关键设计点**：

1. **"请只回复…不要回复其他内容"** —— 这是最直接的边界约束。将这句话放在提示词末尾（近因效应），LLM 更可能遵循。
2. **在约束语句中重复可选项** —— `（FACTUAL / PROCEDURAL / COMPARISON / CHITCHAT）` 再次列出所有有效值，强化记忆。
3. **每个类别附带简短说明** —— 帮助 LLM 理解分类依据，但不给 LLM 留下"需要解释分类理由"的空间。

### 2.3 提供 Few-Shot 示例

Few-shot 是通过示例教会 LLM 期望的输出格式。这是最有效的格式控制手段，尤其对 7B/13B 级别的小模型。

**原则**：
- 每个意图类别至少 1 个示例
- 示例要覆盖典型场景和边界情况
- 示例格式必须与实际期望输出完全一致
- 示例之间用空行分隔，结构清晰

**示例**（IntentClassifier —— 改进后）：

```text
示例：
用户查询：什么是机器学习？
FACTUAL

用户查询：如何安装Python？
PROCEDURAL

用户查询：Python和Java有什么区别？
COMPARISON

用户查询：你好，今天天气不错
CHITCHAT
```

**为什么有效**：

LLM 在看过 4 个示例后，已经建立了稳定的模式映射：

```
"用户查询：{问题}" → "{单个单词}"
```

这远比文字指令"请只回复一个单词"更有效——LLM 对模式的模仿能力远强于对指令的遵循能力。

**示例选择技巧**：

| 意图 | 示例查询 | 选择理由 |
|------|---------|---------|
| FACTUAL | `什么是机器学习？` | 典型的"是什么"类事实查询 |
| PROCEDURAL | `如何安装Python？` | 典型的"如何做"类操作查询 |
| COMPARISON | `Python和Java有什么区别？` | 典型的"A与B对比"类查询 |
| CHITCHAT | `你好，今天天气不错` | 典型的不需要检索的日常寒暄 |

### 2.4 提示词结构总结

一个完整的分类提示词应包含以下层次：

```
[角色设定]        你是谁，做什么
[分类定义]        有哪些类别，每类的含义
[Few-Shot 示例]   输入→输出映射示例
[当前输入]        待分类的实际查询
[输出约束]        格式要求（放在最后，近因效应）
```

---

## 3. 模型参数控制

提示词解决的是"告诉 LLM 做什么"，模型参数解决的是"让 LLM 行为稳定"。

### 3.1 Temperature = 0

Temperature 控制 LLM 输出的随机性。

| Temperature | 行为 | 适用场景 |
|-------------|------|---------|
| `0.0` | 确定性输出，每次相同输入得到相同结果 | **分类、抽取、结构化输出** |
| `0.3~0.5` | 低随机性，输出基本稳定 | 摘要、翻译 |
| `0.7~1.0` | 高随机性，输出多样 | 创意写作、头脑风暴 |
| `> 1.0` | 极高随机性，可能出现不连贯 | 不推荐 |

**意图分类场景必须使用 `temperature = 0.0`**：

- 分类是一个确定性任务——"什么是机器学习？"应该永远被分类为 `FACTUAL`
- 非零温度可能导致相同输入产生不同分类结果，系统行为不可预测
- 温度=0 时 LLM 更倾向于选择最高概率的 token，严格遵循提示词格式

**DeepRagConfig 配置**：

```yaml
llm:
  baseUrl: "http://localhost:11434/v1"
  apiKey: "ollama"
  model: "qwen2.5:7b"
  temperature: 0.0    # 分类/提取任务必须为 0
```

### 3.2 其他相关参数

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `temperature` | `0.0` | 消除随机性 |
| `top_p` | `1.0`（或不设置） | nucleus sampling，temperature=0 时通常不需要 |
| `max_tokens` | 按需设置 | 分类任务只需 1-10 token，限制可防止过度生成 |
| `stop` | `["\n"]` | 阻止 LLM 在答案后继续"脑补" |

> **注意**：LangChain4j 的 `OpenAiChatModel` 通过 `stop` 参数设置 stop 序列。当前代码中 IntentClassifier 的 LLM 是外部传入的，stop 序列应在构建 ChatLanguageModel 时统一配置。

---

## 4. 应用层兜底策略

即使提示词工程和模型参数都做到了极致，LLM 仍然可能产生不可解析的输出（尤其是小模型）。应用层必须做最坏的打算。

### 4.1 三层解析策略

IntentClassifier 采用"由严到松"的三层兜底机制：

```
输入响应
    │
    ▼
┌─────────────────────┐
│ 第一层：精确匹配      │  response.equals("FACTUAL")
│ (expected path)     │  → 直接返回，流程结束
└────────┬────────────┘
         │ 不匹配
         ▼
┌─────────────────────┐
│ 第二层：模糊匹配      │  response.contains("FACTUAL")
│ (degraded path)     │  → 返回 + 记录 WARN 日志
│                     │    供后续提示词优化参考
└────────┬────────────┘
         │ 不匹配
         ▼
┌─────────────────────┐
│ 第三层：默认兜底      │  return Intent.FACTUAL
│ (fallback path)     │  → 记录 ERROR 日志
└─────────────────────┘
```

**Java 实现**：

```java
public Intent classify(String query) {
    try {
        String prompt = String.format(CLASSIFY_PROMPT, query);
        String rawResponse = llm.generate(prompt).trim();
        String response = rawResponse.toUpperCase();

        // 第一层：精确匹配
        for (Intent intent : Intent.values()) {
            if (response.equals(intent.name())) {
                ConsoleLog.step("意图分类: " + intent.name());
                return intent;
            }
        }

        // 第二层：模糊匹配（格式违规但有有效关键词）
        for (Intent intent : Intent.values()) {
            if (response.contains(intent.name())) {
                ConsoleLog.warn("意图分类格式违规，使用模糊匹配 -> "
                    + intent.name() + " (原始响应: " + rawResponse + ")");
                return intent;
            }
        }

        // 第三层：完全无法识别，默认兜底
        ConsoleLog.warn("无法识别意图，使用默认 FACTUAL (原始响应: "
            + rawResponse + ")");
        return Intent.FACTUAL;
    } catch (Exception e) {
        ConsoleLog.error("意图分类失败: " + e.getMessage());
        return Intent.FACTUAL;
    }
}
```

### 4.2 日志分级与可观测性

三层解析的另一个重要价值是**可观测性**：

| 日志级别 | 触发条件 | 含义 |
|----------|---------|------|
| `step` | 精确匹配成功 | 正常运行，无需关注 |
| `warn` | 模糊匹配成功 | **提示词需要优化**，LLM 没有严格遵循格式 |
| `warn` | 无法识别，兜底 | **严重问题**，LLM 输出完全偏离预期 |
| `error` | 异常抛出 | **系统故障**，网络/超时等问题 |

通过监控 `warn` 日志的比例，可以量化评估提示词的有效性：

- `warn率 < 5%`：提示词质量良好
- `warn率 5%~20%`：需要优化提示词或考虑更换模型
- `warn率 > 20%`：当前模型不适合该分类任务

### 4.3 精确匹配 vs 模糊匹配的取舍

**为什么不用更宽松的匹配（如正则、编辑距离）？**

- 对于分类任务，可选项是有限的、精确可枚举的——不需要模糊容错
- 模糊匹配（如 `contains`）已是最大容忍度，再宽松可能引入错误分类
- 如果 LLM 的输出连 `contains` 都匹配不到，说明响应完全不可用，此时兜底比猜测更安全

**枚举顺序的注意点**：

`Intent.values()` 的返回顺序即枚举声明顺序（`FACTUAL → PROCEDURAL → COMPARISON → CHITCHAT`）。如果 LLM 输出 `"介于FACTUAL和PROCEDURAL之间"`，模糊匹配会命中 `FACTUAL`（先遍历到）。这种歧义场景无法完美解决，但通过监控日志可以评估其发生频率。

### 4.4 Stop Token 的作用

Stop token 是防止 LLM "过度生成"的有效手段。对于分类任务：

```
期望输出：FACTUAL
实际输出：FACTUAL\n\n因为这是一个事实查询...
                 ↑
            stop token "\n" 在这里截断
```

在 LangChain4j 中，可以在构建模型时配置 stop 序列。但 IntentClassifier 的 LLM 是由外部传入的，所以 stop 参数应在所有 `OpenAiChatModel.builder()` 调用处统一添加。

---

## 5. 完整方案总览

### 5.1 改进前后对比

| 维度 | 改进前 | 改进后 |
|------|--------|--------|
| **提示词 - 角色** | ✅ 有 | ✅ 有 |
| **提示词 - 边界** | ✅ 有 | ✅ 有 |
| **提示词 - Few-shot** | ❌ 无 | ✅ 4 个示例 |
| **Temperature** | ❌ 未设置 | ✅ 0.0 |
| **解析 - 精确匹配** | ❌ 直接 contains | ✅ equals 优先 |
| **解析 - 模糊匹配** | ✅ contains | ✅ contains + warn 日志 |
| **解析 - 兜底** | ✅ FACTUAL | ✅ FACTUAL |
| **可观测性** | ❌ 无法区分 | ✅ 三级日志分级 |

### 5.2 提示词最终版本

```
你是一个查询意图分类器。请将以下用户查询分类为四种意图之一：
- FACTUAL：事实查询，寻找具体的事实、定义、数据等
- PROCEDURAL：步骤查询，询问如何完成某件事的操作流程
- COMPARISON：对比查询，比较两个或多个事物的异同
- CHITCHAT：闲聊，不需要检索的日常对话

示例：
用户查询：什么是机器学习？
FACTUAL

用户查询：如何安装Python？
PROCEDURAL

用户查询：Python和Java有什么区别？
COMPARISON

用户查询：你好，今天天气不错
CHITCHAT

用户查询：{用户实际输入}

请只回复意图类别名称（FACTUAL / PROCEDURAL / COMPARISON / CHITCHAT），不要回复其他内容。
```

### 5.3 配置变更

**DeepRagConfig.LlmConfig 新增字段**：

```java
private double temperature = 0.0;  // 默认确定性输出
```

**所有 OpenAiChatModel 构建处新增 `.temperature()` 调用**：

```java
ChatLanguageModel chatModel = OpenAiChatModel.builder()
        .baseUrl(config.getLlm().getBaseUrl())
        .apiKey(config.getLlm().getApiKey())
        .modelName(config.getLlm().getModel())
        .temperature(config.getLlm().getTemperature())  // ← 新增
        .timeout(Duration.ofSeconds(config.getLlm().getTimeout()))
        .build();
```

---

## 6. 最佳实践清单

以下清单适用于所有需要 LLM 输出结构化/可解析内容的场景（分类、抽取、实体识别、格式转换等）：

### 提示词层面
- [ ] 赋予 LLM 明确且狭窄的角色（"分类器"而非"助手"）
- [ ] 清晰定义所有可能的输出类别，避免模糊表述
- [ ] 每个类别提供**至少 1 个** few-shot 示例
- [ ] 示例覆盖典型场景和边界场景
- [ ] 输出格式约束放在提示词末尾（近因效应）
- [ ] 使用"只回复…不要回复其他内容"的二元约束

### 参数层面
- [ ] `temperature` 设为 `0.0`
- [ ] 考虑添加 `stop` 序列（如 `"\n"`）防止过度生成
- [ ] 必要时限制 `max_tokens`（分类任务通常只需 5~10 token）

### 解析层面
- [ ] **先精确匹配，再模糊匹配**，不在第一层就做宽松容错
- [ ] 模糊匹配命中时记录 WARN 日志，用于持续优化提示词
- [ ] 设置合理的默认兜底值，确保系统不因解析失败而中断
- [ ] 所有异常捕获后优雅降级，不抛出未处理异常

### 运维层面
- [ ] 监控模糊匹配率和兜底率，作为提示词质量的量化指标
- [ ] 定期抽查 WARN 日志中的原始响应，发现新的失败模式
- [ ] 根据失败模式迭代更新 few-shot 示例

---

> **参考文件**：
> - `src/main/java/com/deeprag/query/IntentClassifier.java` — 意图分类器实现
> - `src/main/java/com/deeprag/config/DeepRagConfig.java` — LLM 温度配置
> - `src/main/java/com/deeprag/query/Intent.java` — 意图枚举定义

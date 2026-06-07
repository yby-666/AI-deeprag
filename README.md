# DeepRAG Engine

<div align="center">

**企业级智能知识检索引擎 （Enterprise Intelligent Knowledge Retrieval Engine）**

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.x-C71A36?logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Milvus](https://img.shields.io/badge/Milvus-2.4%2B-00D3C0?logo=milvus)](https://milvus.io/)
[![Ollama](https://img.shields.io/badge/Ollama-Latest-black?logo=ollama)](https://ollama.ai/)

</div>

---

## 📖 项目简介

DeepRAG Engine 是一个基于 Java 17 构建的**四阶段渐进式 RAG（Retrieval-Augmented Generation）知识检索引擎**，完整实现了从基础 RAG 到 Agentic RAG 的演进路径。项目通过将文档解析、智能分块、向量嵌入、混合检索、重排序、查询理解、策略路由和 LLM 生成等模块组装为可编排的流水线，支持在本地环境中完成企业级知识问答。

### 四阶段演进

| 阶段 | 名称 | 核心能力 |
|:---:|------|---------|
| **Stage 1** | Naive RAG | 基础流水线跑通：解析 → 分块 → 嵌入 → 存储 → Dense 检索 → 生成 → 评估 |
| **Stage 2** | Advanced RAG | 全链路优化：5 种分块策略、查询理解引擎（Rewrite / HyDE / Multi-Query / 意图分类）、Dense + Sparse 混合检索 + RRF 融合、BGE 重排序、引用生成、幻觉检测 |
| **Stage 3** | Advanced Strategies | 自适应策略：Self-RAG（自反思检索）、CRAG（置信度路由）、Adaptive RAG（复杂度自适应路由） |
| **Stage 4** | Agentic RAG | LLM 即 Agent：多轮动态检索决策，LLM 自主判断是否继续检索或直接生成最终答案 |

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        DeepRAG Engine                            │
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────┤
│  Parser  │ Chunker  │ Embedding│  Store   │Retriever │Generator │
│ ─────────│──────────│──────────│──────────│──────────│──────────│
│ PDFBox   │ Fixed    │ Ollama   │ Milvus   │ Dense    │ LangChain│
│ Markdown │ Recursive│ nomic    │ 向量数据库│ Hybrid   │ 4j + LLM │
│          │ Semantic │ embed    │          │ RRF 融合 │ 引用生成  │
│          │ParentChild│ text    │          │          │ 幻觉检测  │
│          │Structure │          │          │          │          │
├──────────┴──────────┴──────────┴──────────┴──────────┴──────────┤
│  查询理解引擎  │      Reranker      │      策略路由层             │
│ ──────────────│──────────────────│─────────────────────────────│
│ 意图分类      │ BGE Reranker      │ Naive → Advanced             │
│ Query Rewrite │ LLM Reranker      │ → Self-RAG → CRAG            │
│ HyDE          │ NoOp Reranker     │ → Adaptive → Agentic         │
│ Multi-Query   │                   │                              │
│ Query 分解    │                   │                              │
├──────────────────────────────────────────────────────────────────┤
│  内置评估框架  │  LLM-as-Judge    │  Hit Rate / MRR / Faithfulness │
└──────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| **语言** | Java 17 | OpenJDK Temurin |
| **构建** | Maven 3.x | maven-shade-plugin 打包 Fat JAR |
| **LLM 框架** | LangChain4j 0.36.2 | OpenAI 兼容协议，对接 Ollama |
| **向量数据库** | Milvus 2.4 | Docker 部署，Standalone 模式 |
| **嵌入模型** | nomic-embed-text (Ollama) | 768 维向量 |
| **LLM** | qwen2.5 (Ollama) | 本地推理 |
| **重排序** | bge-reranker-v2-m3 (Ollama) | Cross-Encoder 重排序 |
| **文档解析** | Apache PDFBox 3.0.4 | PDF 解析 |
| **日志** | SLF4J + Logback | 结构化日志 |
| **HTTP 服务** | JDK HttpServer | 零框架依赖的内置 API 服务 |
| **配置** | SnakeYAML | YAML 配置文件 |

---

## 📁 项目结构

```
AI-deeprag/
├── config/                          # 各阶段配置文件
│   ├── stage1.yml                   # Stage 1 配置（基础）
│   ├── stage2.yml                   # Stage 2 配置（高级）
│   ├── stage3.yml                   # Stage 3 配置（策略）
│   └── stage4.yml                   # Stage 4 配置（Agentic）
├── docker/                          # Docker 部署配置
│   ├── docker-compose.yml           # Milvus Standalone
│   └── monitoring/
│       └── prometheus.yml           # Prometheus 指标采集
├── docs/                            # 文档
│   └── config-guide.md              # 配置指南
├── evaluation/                      # 评估框架
│   └── eval_set.json                # 评估数据集（8 项）
├── testdata/                        # 测试文档
│   ├── deeprag-architecture.md
│   ├── rag-best-practices.md
│   ├── milvus-operations.md
│   └── verification-guide.md
├── src/
│   ├── main/java/com/deeprag/       # 核心模块
│   │   ├── api/                     # HTTP API 层
│   │   ├── chunker/                 # 5 种分块策略
│   │   ├── config/                  # 配置管理
│   │   ├── embedding/               # 嵌入服务
│   │   ├── evaluator/               # 评估框架
│   │   ├── generator/               # 生成层 + 幻觉检测
│   │   ├── parser/                  # 文档解析
│   │   ├── pipeline/                # RAG 流水线
│   │   ├── query/                   # 查询理解引擎
│   │   ├── reranker/                # 重排序器
│   │   ├── retriever/               # 检索器
│   │   ├── store/                   # 向量存储
│   │   └── strategy/                # RAG 策略（7 种）
│   └── stage{1..4}/                 # 四阶段入口应用
│       └── java/com/deeprag/stageN/
│           └── StageNApp.java
└── pom.xml                          # Maven 构建文件
```

---

## 🚀 快速开始

### 前置条件

- **JDK 17+** （推荐 OpenJDK Temurin 17）
- **Maven 3.x**
- **Docker** （运行 Milvus）
- **Ollama** （本地 LLM 推理）

### 1. 拉取 Ollama 模型

```bash
ollama pull qwen2.5
ollama pull nomic-embed-text
ollama pull bge-reranker-v2-m3
```

### 2. 启动 Milvus

```bash
cd docker
docker compose up -d
```

### 3. 编译项目

```bash
mvn compile
```

### 4. 运行

**交互式 CLI 模式：**

```bash
# Stage 1 — Naive RAG
mvn exec:java -Dexec.mainClass="com.deeprag.stage1.Stage1App"

# Stage 2 — Advanced RAG
mvn exec:java -Dexec.mainClass="com.deeprag.stage2.Stage2App"

# Stage 3 — 高级策略（Self-RAG / CRAG / Adaptive）
mvn exec:java -Dexec.mainClass="com.deeprag.stage3.Stage3App"

# Stage 4 — Agentic RAG
mvn exec:java -Dexec.mainClass="com.deeprag.stage4.Stage4App"
```

**打包为 Fat JAR：**

```bash
mvn package
java -jar target/deeprag-engine-1.0.0-SNAPSHOT.jar
```

### 5. CLI 命令

进入交互模式后，支持以下命令：

| 命令 | 说明 | 示例 |
|------|------|------|
| `index <path>` | 索引文档到向量数据库 | `index testdata/deeprag-architecture.md` |
| `query <text>` | 执行 RAG 查询 | `query DeepRAG 的四个阶段是什么？` |
| `evaluate` | 运行评估数据集评测 | `evaluate` |
| `status` | 查看当前状态 | `status` |
| `collections` | 列出所有集合 | `collections` |
| `use <集合名>` | 切换当前集合 | `use deeprag_architecture` |
| `compare` | 多策略对比查询（Stage 2+） | `compare DeepRAG 的核心组件` |
| `strategy` | 切换策略（Stage 3+） | `strategy self_rag` |
| `help` | 显示帮助 | `help` |
| `quit` | 退出 | `quit` |

---

## ⚙️ 配置说明

配置文件位于 `config/` 目录，各阶段独立配置。主要配置项：

```yaml
# LLM 配置
llm:
  baseUrl: "http://localhost:11434/v1"   # Ollama OpenAI 兼容端点
  apiKey: "ollama"                        # Ollama 无需真实密钥
  model: "qwen2.5"
  timeout: 120

# Embedding 配置
embedding:
  baseUrl: "http://localhost:11434"
  model: "nomic-embed-text"
  dimension: 768
  timeout: 60

# 向量存储配置
vectorStore:
  host: "localhost"
  port: 19530
  collectionPrefix: "deeprag_"

# 分块策略
chunker:
  strategy: "recursive"                   # fixed_size / recursive / semantic / parent_child / structure
  maxSize: 500
  overlap: 50

# 检索配置
retriever:
  topK: 5
  scoreThreshold: 0.5

# 评估配置
evaluation:
  datasetPath: "evaluation/eval_set.json"
  reportPath: "evaluation/reports"
```

详细配置说明见 [配置指南](docs/config-guide.md)。

---

## 🧩 核心模块详解

### 分块策略 （Chunker）

| 策略 | 类 | 适用场景 |
|------|-----|---------|
| **Fixed Size** | `FixedSizeChunker` | 通用基线，按固定字符数切分 + 重叠 |
| **Recursive** | `RecursiveChunker` | 结构化技术文档，递归按段落→句子→字符切分 |
| **Semantic** | `SemanticChunker` | 长叙事文档，基于嵌入余弦相似度寻找语义边界 |
| **Parent-Child** | `ParentChildChunker` | 精准检索 + 完整上下文，大块生成 + 小块检索 |
| **Structure-Aware** | `StructureAwareChunker` | 有标题结构的文档，按章节标题切分 |

### 检索策略 （Retriever）

| 策略 | 说明 |
|------|------|
| **Dense** | 向量余弦相似度检索，擅长语义匹配 |
| **Hybrid** | Dense + Sparse（关键词）混合检索，RRF 融合排序，动态权重调整 |

### RAG 策略 （Strategy）

| 策略 | 说明 |
|------|------|
| **NaiveRAG** | 基础检索 → 生成 |
| **AdvancedRAG** | 查询理解 → 混合检索 → 重排序 → 上下文压缩 → 引用生成 → 幻觉检测 |
| **Self-RAG** | LLM 自评估："需要检索？" → "结果相关？" → 按需重试 |
| **CRAG** | 置信度路由：高→直接生成，中→补充检索，低→拒绝回答 |
| **AdaptiveRAG** | 复杂度评估：SIMPLE / MEDIUM / COMPLEX → 分级处理 |
| **AgenticRAG** | LLM 自主决策：RETRIEVE / REWRITE_AND_RETRIEVE / GENERATE 多轮循环 |

### 评估框架 （Evaluator）

内置评估框架支持以下指标：

- **Hit Rate** — 检索命中率
- **MRR** （Mean Reciprocal Rank）— 平均倒数排名
- **Faithfulness** — LLM-as-Judge 忠实度评分
- **Relevance** — LLM-as-Judge 相关性评分

评估数据集：`evaluation/eval_set.json`（8 个测试用例，覆盖 FACTUAL / PROCEDURAL / COMPARISON 类型）

---

## 🌐 HTTP API

项目内置 HTTP API 服务（JDK HttpServer，零框架依赖），启动后可通过 REST 接口访问：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/query` | POST | 知识问答 |
| `/api/v1/documents` | POST | 文档索引 |
| `/api/v1/collections` | GET | 列出集合 |
| `/` | GET | 前端页面 （frontend/index.html） |

---

## 📊 项目特点

- ✅ **四阶段渐进式演进** — 从 Naive 到 Agentic，完整覆盖 RAG 技术发展路径
- ✅ **7 种 RAG 策略** — 策略模式实现，可插拔切换
- ✅ **5 种分块算法** — 覆盖不同文档类型和应用场景
- ✅ **混合检索 + RRF 融合** — 结合向量语义和关键词精确匹配
- ✅ **查询理解引擎** — 意图分类、改写、HyDE、Multi-Query、查询分解
- ✅ **幻觉检测** — 逐声明验证 + LLM 判断
- ✅ **引用生成** — 自动标注答案信息来源
- ✅ **内置评估框架** — LLM-as-Judge + 传统 IR 指标
- ✅ **纯本地部署** — Ollama + Milvus，数据不出域
- ✅ **零框架 API** — JDK 原生 HttpServer，无 Spring 依赖
- ✅ **优雅降级** — 所有外部服务调用均有 try/catch + fallback
- ✅ **中文优化** — 全中文代码注释、文档、提示词

---

## 🔧 开发

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 清理
mvn clean
```

---

## 📝 License

Apache 2.0 License

---

<div align="center">

**DeepRAG Engine** — 从 Naive 到 Agentic，一次构建，全路径演进 🚀

</div>
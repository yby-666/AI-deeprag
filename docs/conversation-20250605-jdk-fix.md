# JDK Misconfigured 问题排查与修复

**日期**: 2025-06-05
**项目**: AI-deeprag (DeepRAG Engine)
**问题文件**: `com.deeprag.stage1.Stage1App`

---

## 问题现象

IDEA 中 `Stage1App.java` 提示 "JDK is misconfigured"，但系统 JDK 配置和环境变量均已正确设置。

## 排查过程

### 1. 系统环境验证 ✅

| 检查项 | 结果 |
|--------|------|
| `JAVA_HOME` | `E:\application\jdk17` |
| `java -version` | OpenJDK 17.0.19 (Temurin) |
| JDK 目录存在 | ✅ `E:\application\jdk17` 完整 |

### 2. 项目配置验证 ✅

| 文件 | 配置项 | 值 |
|------|--------|-----|
| `pom.xml` | `java.version` | 17 |
| `.idea/misc.xml` | `project-jdk-name` | "17" |
| `.idea/AI-deeprag.iml` | `jdkName` | "17" |
| `.idea/compiler.xml` | `bytecodeTargetLevel` | 17 |

### 3. IDEA 全局 JDK 配置 ❌ **根因**

**文件**: `%APPDATA%/JetBrains/IntelliJIdea2024.2/options/jdk.table.xml`

SDK 名为 "17" 的条目存在数据损坏：

```xml
<jdk version="2">
  <name value="17" />
  <version value="Eclipse Temurin 17.0.19" />
  <homePath value="E:/application/jdk17" />   <!-- ✅ 正确 -->
  <!-- ❌ classPath 全部指向已删除的 JDK 21 -->
  <root url="jrt://E:/Program Files/Java/jdk-21.0.10!/java.base" type="simple" />
  ...
</jdk>
```

- `homePath` → `E:/application/jdk17`（JDK 17）✅
- `classPath` 141 条 → 全部指向 `E:/Program Files/Java/jdk-21.0.10`（**已删除**）❌
- `sourcePath` → 同样指向不存在的 JDK 21 ❌

## 修复方案

将 `jdk.table.xml` 中所有 `E:/Program Files/Java/jdk-21.0.10` 替换为 `E:/application/jdk17`：

- 替换 141 处 classPath 引用
- 替换 sourcePath 引用
- 备份文件：`jdk.table.xml.bak`

## 后续操作

1. 完全关闭 IDEA
2. 重新打开项目
3. 验证：File → Project Structure → SDKs → JDK "17" classpath 标签页应显示完整 JDK 17 类库

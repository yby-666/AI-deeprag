package com.deeprag.query;

import com.deeprag.log.ConsoleLog;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * 查询意图分类器
 * <p>
 * 使用 LLM 对用户查询进行意图分类，将其归入 FACTUAL / PROCEDURAL / COMPARISON / CHITCHAT 四类之一
 */
public class IntentClassifier {

    private static final String CLASSIFY_PROMPT = """
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

            用户查询：%s

            请只回复意图类别名称（FACTUAL / PROCEDURAL / COMPARISON / CHITCHAT），不要回复其他内容。
            """;

    private final ChatLanguageModel llm;

    public IntentClassifier(ChatLanguageModel llm) {
        this.llm = llm;
    }

    /**
     * 对查询进行意图分类
     * <p>
     * 解析策略（由严到松的三层兜底）：
     * <ol>
     *   <li>精确匹配：LLM 严格按照指令只输出意图名称</li>
     *   <li>模糊匹配：LLM 输出包含多余文字但意图关键词在响应中</li>
     *   <li>默认兜底：无法识别时返回 FACTUAL</li>
     * </ol>
     *
     * @param query 用户查询文本
     * @return 意图类别，分类失败时默认返回 FACTUAL
     */
    public Intent classify(String query) {
        try {
            String prompt = String.format(CLASSIFY_PROMPT, query);
            String rawResponse = llm.generate(prompt).trim();
            String response = rawResponse.toUpperCase();

            // 第一层：精确匹配（LLM 严格按指令只输出意图名称）
            for (Intent intent : Intent.values()) {
                if (response.equals(intent.name())) {
                    ConsoleLog.step("意图分类: " + intent.name());
                    return intent;
                }
            }

            // 第二层：模糊匹配（LLM 输出包含多余文字，但意图关键词在响应中）
            // 记录格式违规以便持续优化提示词
            for (Intent intent : Intent.values()) {
                if (response.contains(intent.name())) {
                    ConsoleLog.warn("意图分类格式违规，使用模糊匹配 -> " + intent.name()
                            + " (原始响应: " + rawResponse + ")");
                    return intent;
                }
            }

            // 第三层：完全无法识别，默认兜底
            ConsoleLog.warn("无法识别意图，使用默认 FACTUAL (原始响应: " + rawResponse + ")");
            return Intent.FACTUAL;
        } catch (Exception e) {
            ConsoleLog.error("意图分类失败: " + e.getMessage());
            return Intent.FACTUAL;
        }
    }
}

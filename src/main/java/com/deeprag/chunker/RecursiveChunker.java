package com.deeprag.chunker;

import com.deeprag.log.ConsoleLog;
import com.deeprag.parser.ParseResult;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 递归分块器：按分隔符优先级逐级切分
 * 优先使用大分隔符（段落），切不动再用小分隔符（句子、空格、字符）
 */
public class RecursiveChunker implements Chunker {

    // 分隔符优先级：从大到小。先尝试按段落切（\n\n），切不动再按句子切（。！？），最后按字符硬切
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", " ", ""};

    private final int maxSize;

    /**
     * @param maxSize 单个 chunk 的最大字符数
     * @param overlap 保留参数以兼容 {@link Chunker} 各实现的统一构造签名，递归策略内部不使用。
     *                递归按段落→句子→行→字符切分，切出来的片段本身就在语义边界上，
     *                不存在固定窗口硬切导致的"词语被腰斩"问题，overlap 没有收益。
     */
    public RecursiveChunker(int maxSize, int overlap) {
        this.maxSize = maxSize;
    }

    @Override
    public List<Chunk> chunk(ParseResult parseResult) {
        String text = parseResult.getContent();
        Map<String, Object> baseMeta = Chunk.metadataFrom(parseResult.getMetadata());

        ConsoleLog.info("递归分块开始，文本长度: " + text.length());
        List<String> pieces = recursiveSplit(text, 0);

        // 组装 Chunk：递归切分产出的 pieces 本身就在语义边界上（段落/句子/行），
        // 不存在固定窗口硬切导致的"词语被腰斩"问题，因此不需要 overlap 来修复语义断裂。
        // 直接以 pieces 作为最终 chunk，不做前后拼贴。
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;

        for (String piece : pieces) {
            String content = piece.trim();
            if (content.isEmpty()) continue;

            String id = generateId(parseResult.getMetadata().getSource(), index);
            chunks.add(new Chunk(id, content, baseMeta));
            index++;
        }

        ConsoleLog.step("递归分块完成，生成 " + chunks.size() + " 个块");
        return chunks;
    }

    /**
     *   ┌────────┬─────────────────────────────┬────────────────────┐
     *   │ 分隔符  │    split 实际使用的正则         │        原因        │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │ \n\n   │ \n\s*\n                     │ 容错段落间的空白行     │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │ \n     │ \n（escapeRegex 后仍为 \n）   │ 按行切               │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │ 。     │ 。                           │ 按句号切             │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │ ！     │ ！                           │ 按感叹号切           │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │ ？     │ ？                           │ 按问号切             │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │        │  （空格）                     │ 按空格切             │
     *   ├────────┼─────────────────────────────┼────────────────────┤
     *   │ ""     │ 不走 split，直接 hardSplit    │ 按字符硬切            │
     *   └────────┴─────────────────────────────┴────────────────────┘
     * 总结：这行代码本质上就是 text.split(regex)
     *      1、只是对 \n\n 做了增强（支持空白行）对其它分隔符做了正则转义保护。
     *      2、内层的 sep.length() == 1 判断是死代码。（冗余代码）
     */
    private List<String> recursiveSplit(String text, int separatorLevel) {
        if (separatorLevel >= SEPARATORS.length) {
            // 所有分隔符都用完了，强制按 maxSize 切
            return hardSplit(text);
        }

        List<String> result = new ArrayList<>();
        String sep = SEPARATORS[separatorLevel];

        if (sep.isEmpty()) {
            // 最后兜底：按字符硬切
            return hardSplit(text);
        }

        // 按当前分隔符切分
        //  ① \n\n → 用 "\n\s*\n" 匹配段落间可能含空白字符的空行
        //  ② 其他 → escapeRegex 转义正则特殊字符后用作 split 参数
        String[] parts = text.split(sep.equals("\n\n") ? "\n\\s*\n" : escapeRegex(sep));

        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            // 如果当前段落加入后不超过 maxSize，合并
            if (current.isEmpty()) {
                current.append(trimmed);
            } else if (current.length() + sep.length() + trimmed.length() <= maxSize) {
                current.append(sep).append(trimmed);
            } else {
                // 当前段落已满，先保存
                result.add(current.toString());
                current = new StringBuilder(trimmed);
            }
        }

        // 收尾：假设最后一段永远不会"超限"触发写入。循环中只有 current 满了才会写入 result，循环结束后在 current 中的最后一段也要加入
        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        // 对仍然过大的块，用下一级分隔符继续切
        List<String> finalResult = new ArrayList<>();
        for (String piece : result) {
            if (piece.length() > maxSize) {
                finalResult.addAll(recursiveSplit(piece, separatorLevel + 1));
            } else {
                finalResult.add(piece);
            }
        }

        return finalResult;
    }

    /**
     * 强制硬切：按 maxSize 长度切分，作为最终兜底
     */
    private List<String> hardSplit(String text) {
        List<String> result = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + maxSize, text.length());
            result.add(text.substring(pos, end));
            pos = end;
        }
        return result;
    }

    /**
     * 转义正则特殊字符 防御性地对所有字面字符串做安全转义
     * 它匹配正则的 12 个特殊元字符，在前面加 \ 转义。比如输入 . → 输出 \.（匹配字面句点而非"任意字符"）
     */
    private String escapeRegex(String s) {
        return s.replaceAll("([\\\\\\[\\](){}.*+?^$|])", "\\\\$1");
    }

    private String generateId(String source, int index) {
        try {
            String raw = Path.of(source).getFileName().toString() + "_" + index;
            byte[] hash = MessageDigest.getInstance("MD5").digest(raw.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 12);
        } catch (Exception e) {
            return "chunk_" + index;
        }
    }
}

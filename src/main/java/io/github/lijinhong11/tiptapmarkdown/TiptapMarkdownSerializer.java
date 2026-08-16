package io.github.lijinhong11.tiptapmarkdown;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class TiptapMarkdownSerializer {
    private final String indentString;
    private final Map<String, MarkdownExtension.NodeRenderer> customRenderers;

    TiptapMarkdownSerializer(MarkdownOptions options) {
        char indentCharacter = options.getIndentationStyle() == MarkdownOptions.IndentationStyle.SPACE ? ' ' : '\t';
        indentString = repeat(indentCharacter, options.getIndentationSize());
        customRenderers = new LinkedHashMap<String, MarkdownExtension.NodeRenderer>();
        for (MarkdownExtension extension : options.getMarkdownExtensions()) {
            if (extension.getRenderer() != null) {
                customRenderers.put(extension.getNodeType(), extension.getRenderer());
            }
        }
    }

    String serialize(JsonNode document) {
        if (document.isArray()) {
            return renderBlocks(document);
        }
        if ("doc".equals(document.path("type").asText())) {
            return renderBlocks(document.path("content"));
        }
        return renderBlock(document);
    }

    private String renderBlocks(JsonNode content) {
        if (!content.isArray()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        content.forEach(node -> blocks.add(renderBlock(node)));
        return String.join("\n\n", blocks);
    }

    private String renderBlock(JsonNode node) {
        String type = node.path("type").asText();
        MarkdownExtension.NodeRenderer customRenderer = customRenderers.get(type);
        if (customRenderer != null) return customRenderer.render(node, renderContext());
        if ("doc".equals(type)) return renderBlocks(node.path("content"));
        if ("paragraph".equals(type)) return renderInline(node.path("content"));
        if ("heading".equals(type)) return repeat('#', clamp(node.path("attrs").path("level").asInt(1), 1, 6)) + " " + renderInline(node.path("content"));
        if ("blockquote".equals(type)) return prefixLines(renderBlocks(node.path("content")), "> ");
        if ("codeBlock".equals(type)) return renderCodeBlock(node);
        if ("horizontalRule".equals(type)) return "---";
        if ("bulletList".equals(type)) return renderList(node, false, false);
        if ("orderedList".equals(type)) return renderList(node, true, false);
        if ("taskList".equals(type)) return renderList(node, false, true);
        if ("table".equals(type)) return renderTable(node);
        if ("text".equals(type) || "hardBreak".equals(type) || "image".equals(type)) return renderInlineNode(node);
        return renderBlocks(node.path("content"));
    }

    private String renderCodeBlock(JsonNode node) {
        String language = node.path("attrs").path("language").asText("");
        String value = plainText(node.path("content"));
        int longestRun = longestRun(value, '`');
        String fence = repeat('`', Math.max(3, longestRun + 1));
        return fence + language + "\n" + value + "\n" + fence;
    }

    private String renderList(JsonNode list, boolean ordered, boolean tasks) {
        JsonNode content = list.path("content");
        if (!content.isArray()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        int number = list.path("attrs").path("start").asInt(1);
        for (JsonNode item : content) {
            String marker;
            if (tasks) {
                marker = item.path("attrs").path("checked").asBoolean() ? "- [x] " : "- [ ] ";
            } else if (ordered) {
                marker = number++ + ". ";
            } else {
                marker = "- ";
            }
            String body = renderListItem(item);
            String continuation = indentString;
            items.add(marker + body.replace("\n", "\n" + continuation));
        }
        return String.join("\n", items);
    }

    private String renderListItem(JsonNode item) {
        JsonNode content = item.path("content");
        if (!content.isArray()) {
            return "";
        }
        List<String> blocks = new ArrayList<>();
        content.forEach(node -> blocks.add(renderBlock(node)));
        return String.join("\n", blocks);
    }

    private String renderTable(JsonNode table) {
        JsonNode rows = table.path("content");
        if (!rows.isArray() || rows.isEmpty()) {
            return "";
        }
        int columns = 0;
        for (JsonNode row : rows) {
            columns = Math.max(columns, row.path("content").size());
        }
        List<String> lines = new ArrayList<>();
        lines.add(renderTableRow(rows.get(0), columns));
        List<String> separators = new ArrayList<String>();
        for (int i = 0; i < columns; i++) {
            JsonNode cell = i < rows.get(0).path("content").size()
                    ? rows.get(0).path("content").get(i) : null;
            String alignment = cell == null ? "" : cell.path("attrs").path("align").asText("");
            if ("left".equals(alignment)) separators.add(":---");
            else if ("center".equals(alignment)) separators.add(":---:");
            else if ("right".equals(alignment)) separators.add("---:");
            else separators.add("---");
        }
        lines.add("| " + String.join(" | ", separators) + " |");
        for (int i = 1; i < rows.size(); i++) {
            lines.add(renderTableRow(rows.get(i), columns));
        }
        return String.join("\n", lines);
    }

    private String renderTableRow(JsonNode row, int columns) {
        List<String> cells = new ArrayList<>();
        JsonNode content = row.path("content");
        for (int i = 0; i < columns; i++) {
            String cell = i < content.size() ? renderBlocks(content.get(i).path("content")) : "";
            cells.add(cell.replace("|", "\\|").replace("\n", "<br>"));
        }
        return "| " + String.join(" | ", cells) + " |";
    }

    private String renderInline(JsonNode content) {
        if (!content.isArray()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JsonNode current = content.get(i);
            if (!"text".equals(current.path("type").asText())) {
                result.append(renderInlineNode(current));
                continue;
            }
            StringBuilder text = new StringBuilder(current.path("text").asText(""));
            int end = i;
            while (end + 1 < content.size() && "text".equals(content.get(end + 1).path("type").asText())
                    && current.path("marks").equals(content.get(end + 1).path("marks"))) {
                text.append(content.get(++end).path("text").asText(""));
            }
            if (end == i) {
                result.append(renderMarkedText(current));
            } else {
                result.append(renderMarkedText(current, text.toString()));
                i = end;
            }
        }
        return result.toString();
    }

    private String renderInlineNode(JsonNode node) {
        String type = node.path("type").asText();
        MarkdownExtension.NodeRenderer customRenderer = customRenderers.get(type);
        if (customRenderer != null) return customRenderer.render(node, renderContext());
        if ("text".equals(type)) return renderMarkedText(node);
        if ("hardBreak".equals(type)) return "  \n";
        if ("image".equals(type)) return renderImage(node);
        return renderInline(node.path("content"));
    }

    private String renderMarkedText(JsonNode node) {
        return renderMarkedText(node, node.path("text").asText(""));
    }

    private String renderMarkedText(JsonNode node, String text) {
        String value = text;
        JsonNode marks = node.path("marks");
        if (!hasMark(marks, "code")) {
            value = escapeText(value);
        }
        if (marks.isArray()) {
            Iterator<JsonNode> iterator = marks.elements();
            while (iterator.hasNext()) {
                value = applyMark(value, iterator.next());
            }
        }
        return value;
    }

    private String applyMark(String value, JsonNode mark) {
        String type = mark.path("type").asText();
        if ("bold".equals(type)) return wrapWithWhitespace(value, "**", "**");
        if ("italic".equals(type)) return wrapWithWhitespace(value, "*", "*");
        if ("strike".equals(type)) return wrapWithWhitespace(value, "~~", "~~");
        if ("underline".equals(type)) return wrapWithWhitespace(value, "++", "++");
        if ("code".equals(type)) return renderCodeMark(value);
        if ("link".equals(type)) return renderLink(value, mark.path("attrs"));
        return value;
    }

    private String renderCodeMark(String value) {
        String delimiter = repeat('`', Math.max(1, longestRun(value, '`') + 1));
        String padding = value.startsWith("`") || value.endsWith("`") ? " " : "";
        return delimiter + padding + value + padding + delimiter;
    }

    private String renderLink(String value, JsonNode attrs) {
        String href = attrs.path("href").asText("").replace(")", "\\)");
        String title = attrs.path("title").asText("");
        return "[" + value + "](" + href + (title.isEmpty() ? "" : " \"" + title.replace("\"", "\\\"") + "\"") + ")";
    }

    private String renderImage(JsonNode node) {
        JsonNode attrs = node.path("attrs");
        String alt = attrs.path("alt").asText("").replace("]", "\\]");
        String src = attrs.path("src").asText("").replace(")", "\\)");
        String title = attrs.path("title").asText("");
        return "![" + alt + "](" + src + (title.isEmpty() ? "" : " \"" + title.replace("\"", "\\\"") + "\"") + ")";
    }

    private String wrapWithWhitespace(String value, String open, String close) {
        int leading = 0;
        while (leading < value.length() && Character.isWhitespace(value.charAt(leading))) {
            leading++;
        }
        int trailing = value.length();
        while (trailing > leading && Character.isWhitespace(value.charAt(trailing - 1))) {
            trailing--;
        }
        if (leading == trailing) {
            return value;
        }
        return value.substring(0, leading) + open + value.substring(leading, trailing) + close + value.substring(trailing);
    }

    private String escapeText(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        boolean lineStart = true;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            boolean structuralStart = lineStart && (character == '#' || character == '>' || character == '-'
                    || character == '+' || Character.isDigit(character));
            if (structuralStart || character == '\\' || character == '`' || character == '*' || character == '_'
                    || character == '[' || character == ']' || character == '~') {
                escaped.append('\\');
            }
            escaped.append(character);
            lineStart = character == '\n';
        }
        return escaped.toString();
    }

    private boolean hasMark(JsonNode marks, String type) {
        if (!marks.isArray()) {
            return false;
        }
        for (JsonNode mark : marks) {
            if (type.equals(mark.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private String plainText(JsonNode content) {
        if (!content.isArray()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        content.forEach(node -> result.append(node.path("text").asText("")));
        return result.toString();
    }

    private String prefixLines(String value, String prefix) {
        return prefix + value.replace("\n", "\n" + prefix);
    }

    private int longestRun(String value, char target) {
        int longest = 0;
        int current = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                longest = Math.max(longest, ++current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(character);
        }
        return result.toString();
    }

    private MarkdownExtension.RenderContext renderContext() {
        return new MarkdownExtension.RenderContext() {
            @Override
            public String renderBlocks(JsonNode content) {
                return TiptapMarkdownSerializer.this.renderBlocks(content);
            }

            @Override
            public String renderInline(JsonNode content) {
                return TiptapMarkdownSerializer.this.renderInline(content);
            }

            @Override
            public String indent(String content) {
                return indentString + content.replace("\n", "\n" + indentString);
            }
        };
    }
}

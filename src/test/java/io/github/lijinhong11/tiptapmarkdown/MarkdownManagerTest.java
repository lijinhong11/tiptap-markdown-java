package io.github.lijinhong11.tiptapmarkdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.ast.Heading;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownManagerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarkdownManager manager = new MarkdownManager(mapper);

    @Test
    void parsesBlocksAndNestedMarks() {
        JsonNode doc = manager.parse("# Title\n\nA **bold and *italic*** [link](https://example.com \"site\").");

        assertEquals("doc", doc.path("type").asText());
        assertEquals("heading", doc.path("content").get(0).path("type").asText());
        assertEquals(1, doc.path("content").get(0).path("attrs").path("level").asInt());
        assertTrue(hasMark(findText(doc, "bold and "), "bold"));
        assertTrue(hasMark(findText(doc, "italic"), "bold"));
        assertTrue(hasMark(findText(doc, "italic"), "italic"));
        JsonNode link = findText(doc, "link");
        assertTrue(hasMark(link, "link"));
        assertEquals("https://example.com", mark(link, "link").path("attrs").path("href").asText());
    }

    @Test
    void parsesListsCodeAndTables() {
        String markdown = "- [x] shipped\n- [ ] pending\n\n```java\nint n = 1;\n```\n\n| Name | Value |\n| --- | --- |\n| one | **two** |";

        JsonNode doc = manager.parse(markdown);

        JsonNode taskList = doc.path("content").get(0);
        assertEquals("taskList", taskList.path("type").asText());
        assertTrue(taskList.path("content").get(0).path("attrs").path("checked").asBoolean());
        assertFalse(taskList.path("content").get(1).path("attrs").path("checked").asBoolean());

        JsonNode code = doc.path("content").get(1);
        assertEquals("codeBlock", code.path("type").asText());
        assertEquals("java", code.path("attrs").path("language").asText());
        assertEquals("int n = 1;", code.path("content").get(0).path("text").asText());

        JsonNode table = doc.path("content").get(2);
        assertEquals("table", table.path("type").asText());
        assertEquals("tableHeader", table.path("content").get(0).path("content").get(0).path("type").asText());
        assertTrue(hasMark(findText(table, "two"), "bold"));
    }

    @Test
    void serializesTiptapJson() throws Exception {
        JsonNode doc = mapper.readTree("{\"type\":\"doc\",\"content\":["
                + "{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"world\",\"marks\":[{\"type\":\"bold\"},{\"type\":\"link\",\"attrs\":{\"href\":\"https://example.com\"}}]}]},"
                + "{\"type\":\"orderedList\",\"attrs\":{\"start\":3},\"content\":[{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"third\"}]}]}]}"
                + "]}");

        assertEquals("## Hello\n\n[**world**](https://example.com)\n\n3. third", manager.serialize(doc));
    }

    @Test
    void roundTripsSupportedMarkdown() {
        String markdown = "> Quote\n\n- one\n- two\n\nText with ~~strike~~, `code`, and ![alt](image.png \"title\").";

        String serialized = manager.serialize(manager.parse(markdown));

        assertEquals(markdown, serialized);
    }

    @Test
    void parsesEmptyDocument() {
        JsonNode doc = manager.parse("");

        assertTrue(doc.path("content").isArray());
        assertTrue(doc.path("content").isEmpty());
        assertEquals("", manager.serialize(doc));
    }

    @Test
    void parsesLinksEntitiesAndMixedTaskLists() {
        String markdown = "Visit https://example.com and <mail@example.com>. &amp; [docs][d] ++underlined++\n\n"
                + "- plain\n- [x] task\n- plain again\n\n[d]: https://docs.example.com \"Docs\"";

        JsonNode doc = manager.parse(markdown);

        assertEquals("https://example.com", mark(findText(doc, "https://example.com"), "link")
                .path("attrs").path("href").asText());
        assertEquals("mailto:mail@example.com", mark(findText(doc, "mail@example.com"), "link")
                .path("attrs").path("href").asText());
        assertEquals("&", findText(doc, "&").path("text").asText());
        assertEquals("https://docs.example.com", mark(findText(doc, "docs"), "link")
                .path("attrs").path("href").asText());
        assertTrue(hasMark(findText(doc, "underlined"), "underline"));
        assertEquals("bulletList", doc.path("content").get(1).path("type").asText());
        assertEquals("taskList", doc.path("content").get(2).path("type").asText());
        assertEquals("bulletList", doc.path("content").get(3).path("type").asText());
    }

    @Test
    void preservesTableAlignmentAndAdjacentMarks() throws Exception {
        String table = "| Left | Center | Right |\n| :--- | :---: | ---: |\n| a | b | c |";
        JsonNode parsed = manager.parse(table);

        assertEquals("left", parsed.path("content").get(0).path("content").get(0)
                .path("content").get(0).path("attrs").path("align").asText());
        assertEquals("center", parsed.path("content").get(0).path("content").get(0)
                .path("content").get(1).path("attrs").path("align").asText());
        assertEquals(table, manager.serialize(parsed));

        JsonNode adjacent = mapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"one\",\"marks\":[{\"type\":\"italic\"}]},"
                + "{\"type\":\"text\",\"text\":\"two\",\"marks\":[{\"type\":\"italic\"}]}]}]}");
        assertEquals("*onetwo*", manager.serialize(adjacent));
    }

    @Test
    void supportsCustomHandlersAndIndentation() {
        MarkdownExtension headingExtension = MarkdownExtension.of(
                Heading.class,
                (node, context) -> {
                    ObjectNode result = mapper.createObjectNode().put("type", "title");
                    result.set("content", context.parseInlineChildren(node));
                    return result;
                },
                "title",
                (node, context) -> "TITLE: " + context.renderInline(node.path("content"))
        );
        MarkdownOptions options = MarkdownOptions.builder()
                .indentation(MarkdownOptions.IndentationStyle.TAB, 1)
                .addExtension(headingExtension)
                .build();
        MarkdownManager custom = new MarkdownManager(mapper, options);

        JsonNode parsed = custom.parse("# Custom");
        assertEquals("title", parsed.path("content").get(0).path("type").asText());
        assertEquals("TITLE: Custom", custom.serialize(parsed));
        assertEquals("\t", custom.getIndentCharacter());
        assertEquals("\t", custom.getIndentString());
    }

    @Test
    void escapesBlockSyntaxInParagraphTextAndKeepsFinalHardBreak() throws Exception {
        JsonNode doc = mapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"# title\\n- item\\n> quote\"},"
                + "{\"type\":\"hardBreak\"}]}]}");

        assertEquals("\\# title\n\\- item\n\\> quote  \n", manager.serialize(doc));
    }

    private JsonNode findText(JsonNode node, String text) {
        if ("text".equals(node.path("type").asText()) && text.equals(node.path("text").asText())) {
            return node;
        }
        for (JsonNode child : node.path("content")) {
            JsonNode found = findText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean hasMark(JsonNode node, String type) {
        return mark(node, type) != null;
    }

    private JsonNode mark(JsonNode node, String type) {
        if (node == null) {
            return null;
        }
        for (JsonNode mark : node.path("marks")) {
            if (type.equals(mark.path("type").asText())) {
                return mark;
            }
        }
        return null;
    }
}

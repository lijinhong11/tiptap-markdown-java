package io.github.lijinhong11.tiptapmarkdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.ins.Ins;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TiptapMarkdownParser {
    private final ObjectMapper mapper;
    private final Map<Class<? extends Node>, MarkdownExtension.NodeParser> customParsers;
    private Document document;

    TiptapMarkdownParser(ObjectMapper mapper, List<MarkdownExtension> extensions) {
        this.mapper = mapper;
        this.customParsers = new LinkedHashMap<Class<? extends Node>, MarkdownExtension.NodeParser>();
        for (MarkdownExtension extension : extensions) {
            if (extension.getParser() != null) {
                customParsers.put(extension.getNodeClass(), extension.getParser());
            }
        }
    }

    ObjectNode parse(Node document) {
        this.document = (Document) document;
        ObjectNode result = node("doc");
        result.set("content", blockChildren(document));
        return result;
    }

    private ArrayNode blockChildren(Node parent) {
        ArrayNode content = mapper.createArrayNode();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            JsonNode parsed = parseBlock(child);
            if (parsed != null) {
                if (parsed.isArray()) {
                    content.addAll((ArrayNode) parsed);
                } else {
                    content.add(parsed);
                }
            }
        }
        return content;
    }

    private JsonNode parseBlock(Node source) {
        JsonNode custom = parseCustom(source);
        if (custom != null) {
            return custom;
        }
        if (source instanceof Paragraph) {
            return withContent(node("paragraph"), inlineChildren(source, Collections.<ObjectNode>emptyList()));
        }
        if (source instanceof Heading) {
            Heading heading = (Heading) source;
            ObjectNode result = withContent(node("heading"), inlineChildren(source, Collections.<ObjectNode>emptyList()));
            result.putObject("attrs").put("level", heading.getLevel());
            return result;
        }
        if (source instanceof BlockQuote) {
            return withContent(node("blockquote"), blockChildren(source));
        }
        if (source instanceof FencedCodeBlock) {
            FencedCodeBlock code = (FencedCodeBlock) source;
            return codeBlock(code.getContentChars().toString(), code.getInfo().toString());
        }
        if (source instanceof IndentedCodeBlock) {
            IndentedCodeBlock code = (IndentedCodeBlock) source;
            return codeBlock(code.getContentChars().toString(), "");
        }
        if (source instanceof ThematicBreak) {
            return node("horizontalRule");
        }
        if (source instanceof BulletList) {
            BulletList list = (BulletList) source;
            return parseBulletList(list);
        }
        if (source instanceof OrderedList) {
            OrderedList list = (OrderedList) source;
            ObjectNode result = withContent(node("orderedList"), listItems(list, false));
            result.putObject("attrs").put("start", list.getStartNumber());
            return result;
        }
        if (source instanceof TableBlock) {
            return withContent(node("table"), tableRows(source));
        }
        if (source instanceof HtmlBlock) {
            HtmlBlock html = (HtmlBlock) source;
            return withContent(node("paragraph"), textContent(html.getChars().toString(), Collections.<ObjectNode>emptyList()));
        }
        return null;
    }

    private JsonNode parseCustom(Node source) {
        for (Map.Entry<Class<? extends Node>, MarkdownExtension.NodeParser> entry : customParsers.entrySet()) {
            if (entry.getKey().isInstance(source)) {
                return entry.getValue().parse(source, new MarkdownExtension.ParseContext() {
                    @Override
                    public ArrayNode parseBlockChildren(Node parent) {
                        return blockChildren(parent);
                    }

                    @Override
                    public ArrayNode parseInlineChildren(Node parent) {
                        return inlineChildren(parent, Collections.<ObjectNode>emptyList());
                    }
                });
            }
        }
        return null;
    }

    private JsonNode parseBulletList(BulletList list) {
        ArrayNode groups = mapper.createArrayNode();
        ArrayNode currentItems = mapper.createArrayNode();
        Boolean currentTaskType = null;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem)) {
                continue;
            }
            boolean task = child instanceof TaskListItem;
            if (currentTaskType != null && currentTaskType.booleanValue() != task) {
                groups.add(withContent(node(currentTaskType.booleanValue() ? "taskList" : "bulletList"), currentItems));
                currentItems = mapper.createArrayNode();
            }
            currentTaskType = task;
            currentItems.add(parseListItem(child, task));
        }
        if (currentTaskType != null) {
            groups.add(withContent(node(currentTaskType.booleanValue() ? "taskList" : "bulletList"), currentItems));
        }
        return groups.size() == 1 ? groups.get(0) : groups;
    }

    private ObjectNode codeBlock(String value, String language) {
        ObjectNode result = node("codeBlock");
        ObjectNode attrs = result.putObject("attrs");
        String normalizedLanguage = language.trim().split("\\s+", 2)[0];
        if (!normalizedLanguage.isEmpty()) {
            attrs.put("language", normalizedLanguage);
        } else {
            attrs.putNull("language");
        }
        String text = value.replaceFirst("\\r?\\n$", "");
        if (!text.isEmpty()) {
            result.set("content", textContent(text, Collections.<ObjectNode>emptyList()));
        }
        return result;
    }

    private boolean hasTaskItems(Node list) {
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TaskListItem) {
                return true;
            }
        }
        return false;
    }

    private ArrayNode listItems(Node list, boolean taskList) {
        ArrayNode content = mapper.createArrayNode();
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem)) {
                continue;
            }
            content.add(parseListItem(child, taskList));
        }
        return content;
    }

    private ObjectNode parseListItem(Node source, boolean task) {
        ObjectNode item = node(task ? "taskItem" : "listItem");
        if (task) {
            item.putObject("attrs").put("checked", source instanceof TaskListItem
                    && ((TaskListItem) source).isItemDoneMarker());
        }
        item.set("content", blockChildren(source));
        return item;
    }

    private ArrayNode tableRows(Node table) {
        ArrayNode rows = mapper.createArrayNode();
        collectTableRows(table, rows);
        return rows;
    }

    private void collectTableRows(Node parent, ArrayNode rows) {
        if (parent instanceof TableSeparator) {
            return;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableRow) {
                TableRow row = (TableRow) child;
                ObjectNode parsedRow = node("tableRow");
                ArrayNode cells = mapper.createArrayNode();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (cell instanceof TableCell) {
                        TableCell tableCell = (TableCell) cell;
                        String type = isInTableHead(row) ? "tableHeader" : "tableCell";
                        ObjectNode parsedCell = node(type);
                        TableCell.Alignment alignment = tableCell.getAlignment();
                        if (alignment != null) {
                            parsedCell.putObject("attrs").put("align", alignment.name().toLowerCase());
                        }
                        ArrayNode paragraphs = mapper.createArrayNode();
                        paragraphs.add(withContent(node("paragraph"), inlineChildren(tableCell, Collections.<ObjectNode>emptyList())));
                        parsedCell.set("content", paragraphs);
                        cells.add(parsedCell);
                    }
                }
                parsedRow.set("content", cells);
                rows.add(parsedRow);
            } else {
                collectTableRows(child, rows);
            }
        }
    }

    private boolean isInTableHead(Node row) {
        for (Node parent = row.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TableHead) {
                return true;
            }
            if (parent instanceof TableBlock) {
                break;
            }
        }
        return false;
    }

    private ArrayNode inlineChildren(Node parent, List<ObjectNode> marks) {
        ArrayNode content = mapper.createArrayNode();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            appendInline(child, marks, content);
        }
        return content;
    }

    private void appendInline(Node source, List<ObjectNode> marks, ArrayNode target) {
        JsonNode custom = parseCustom(source);
        if (custom != null) {
            if (custom.isArray()) {
                target.addAll((ArrayNode) custom);
            } else {
                target.add(custom);
            }
        } else if (source instanceof Text) {
            Text text = (Text) source;
            appendText(target, text.getChars().toString(), marks);
        } else if (source instanceof SoftLineBreak) {
            appendText(target, "\n", marks);
        } else if (source instanceof HardLineBreak) {
            target.add(node("hardBreak"));
        } else if (source instanceof Code) {
            Code code = (Code) source;
            appendText(target, code.getText().toString(), addMark(marks, mark("code")));
        } else if (source instanceof StrongEmphasis) {
            appendChildren(source, addMark(marks, mark("bold")), target);
        } else if (source instanceof Emphasis) {
            appendChildren(source, addMark(marks, mark("italic")), target);
        } else if (source instanceof Strikethrough) {
            appendChildren(source, addMark(marks, mark("strike")), target);
        } else if (source instanceof Ins) {
            appendChildren(source, addMark(marks, mark("underline")), target);
        } else if (source instanceof Link) {
            Link link = (Link) source;
            ObjectNode linkMark = mark("link");
            ObjectNode attrs = linkMark.putObject("attrs").put("href", link.getUrl().toString());
            String title = link.getTitle().toString();
            if (!title.isEmpty()) {
                attrs.put("title", title);
            }
            appendChildren(source, addMark(marks, linkMark), target);
        } else if (source instanceof Image) {
            Image image = (Image) source;
            ObjectNode result = node("image");
            ObjectNode attrs = result.putObject("attrs");
            attrs.put("src", image.getUrl().toString());
            attrs.put("alt", image.getText().toString());
            String title = image.getTitle().toString();
            if (!title.isEmpty()) {
                attrs.put("title", title);
            }
            target.add(result);
        } else if (source instanceof LinkRef) {
            LinkRef link = (LinkRef) source;
            Reference reference = link.getReferenceNode(document);
            if (reference == null) {
                appendText(target, link.getText().toString(), marks);
            } else {
                appendLinkText(target, link.getText().toString(), reference.getUrl().toString(),
                        reference.getTitle().toString(), marks);
            }
        } else if (source instanceof ImageRef) {
            ImageRef image = (ImageRef) source;
            Reference reference = image.getReferenceNode(document);
            if (reference != null) {
                ObjectNode result = node("image");
                ObjectNode attrs = result.putObject("attrs");
                attrs.put("src", reference.getUrl().toString());
                attrs.put("alt", image.getText().toString());
                if (!reference.getTitle().isEmpty()) {
                    attrs.put("title", reference.getTitle().toString());
                }
                target.add(result);
            } else {
                appendText(target, image.getChars().toString(), marks);
            }
        } else if (source instanceof AutoLink) {
            AutoLink link = (AutoLink) source;
            appendLinkText(target, link.getText().toString(), link.getText().toString(), "", marks);
        } else if (source instanceof MailLink) {
            MailLink link = (MailLink) source;
            String address = link.getText().toString();
            appendLinkText(target, address, "mailto:" + address, "", marks);
        } else if (source instanceof HtmlEntity) {
            appendText(target, source.getChars().unescape().toString(), marks);
        } else if (source instanceof HtmlInline) {
            HtmlInline html = (HtmlInline) source;
            appendText(target, html.getChars().toString(), marks);
        } else {
            appendChildren(source, marks, target);
        }
    }

    private void appendLinkText(ArrayNode target, String text, String href, String title, List<ObjectNode> marks) {
        ObjectNode linkMark = mark("link");
        ObjectNode attrs = linkMark.putObject("attrs").put("href", href);
        if (!title.isEmpty()) {
            attrs.put("title", title);
        }
        appendText(target, text, addMark(marks, linkMark));
    }

    private void appendChildren(Node parent, List<ObjectNode> marks, ArrayNode target) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            appendInline(child, marks, target);
        }
    }

    private void appendText(ArrayNode target, String value, List<ObjectNode> marks) {
        if (value.isEmpty()) {
            return;
        }
        if (!target.isEmpty()) {
            ObjectNode previous = (ObjectNode) target.get(target.size() - 1);
            if ("text".equals(previous.path("type").asText()) && previous.path("marks").equals(mapper.valueToTree(marks))) {
                previous.put("text", previous.path("text").asText() + value);
                return;
            }
        }
        ObjectNode result = node("text").put("text", value);
        if (!marks.isEmpty()) {
            ArrayNode markArray = mapper.createArrayNode();
            marks.forEach(markArray::add);
            result.set("marks", markArray);
        }
        target.add(result);
    }

    private ArrayNode textContent(String text, List<ObjectNode> marks) {
        ArrayNode result = mapper.createArrayNode();
        appendText(result, text, marks);
        return result;
    }

    private List<ObjectNode> addMark(List<ObjectNode> marks, ObjectNode mark) {
        List<ObjectNode> result = new ArrayList<>(marks);
        result.add(mark);
        return result;
    }

    private ObjectNode mark(String type) {
        return node(type);
    }

    private ObjectNode node(String type) {
        return mapper.createObjectNode().put("type", type);
    }

    private ObjectNode withContent(ObjectNode node, ArrayNode content) {
        if (!content.isEmpty()) {
            node.set("content", content);
        }
        return node;
    }
}

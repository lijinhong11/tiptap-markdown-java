package io.github.lijinhong11.tiptapmarkdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vladsch.flexmark.util.ast.Node;

/**
 * Connects custom flexmark AST nodes with custom Tiptap JSON node types.
 *
 * <p>An extension may provide only a parser, only a renderer, or both. A
 * bidirectional extension can be declared in one place:</p>
 *
 * <pre>{@code
 * MarkdownExtension title = MarkdownExtension.of(
 *     Heading.class,
 *     (node, context) -> {
 *         ObjectNode json = mapper.createObjectNode().put("type", "title");
 *         json.set("content", context.parseInlineChildren(node));
 *         return json;
 *     },
 *     "title",
 *     (node, context) ->
 *         "TITLE: " + context.renderInline(node.path("content"))
 * );
 * }</pre>
 *
 * <p>Parsers are matched with {@link Class#isInstance(Object)}, so a handler
 * may target a concrete AST class or a shared superclass. Custom handlers are
 * consulted before built-in conversion rules.</p>
 *
 * @see MarkdownOptions.Builder#addExtension(MarkdownExtension)
 */
public final class MarkdownExtension {
    /** Helper operations available while converting a flexmark AST node. */
    public interface ParseContext {
        /**
         * Converts all direct block children of an AST node.
         *
         * @param parent flexmark parent node
         * @return newly allocated Tiptap content array
         */
        ArrayNode parseBlockChildren(Node parent);

        /**
         * Converts all direct inline children of an AST node.
         *
         * @param parent flexmark parent node
         * @return newly allocated Tiptap inline content array
         */
        ArrayNode parseInlineChildren(Node parent);
    }

    /** Converts one flexmark AST node into Tiptap JSON. */
    @FunctionalInterface
    public interface NodeParser {
        /**
         * Parses a matched AST node.
         *
         * <p>The result may be one JSON node or an array of nodes. Returning
         * {@code null} falls back to the manager's built-in handling.</p>
         *
         * @param node matched flexmark AST node
         * @param context recursive child-conversion helpers
         * @return Tiptap JSON node, node array, or {@code null}
         */
        JsonNode parse(Node node, ParseContext context);
    }

    /** Helper operations available while rendering a Tiptap JSON node. */
    public interface RenderContext {
        /**
         * Renders a JSON content array as block Markdown.
         *
         * @param content Tiptap block content array
         * @return rendered blocks separated according to manager rules
         */
        String renderBlocks(JsonNode content);

        /**
         * Renders a JSON content array as inline Markdown.
         *
         * @param content Tiptap inline content array
         * @return rendered inline Markdown
         */
        String renderInline(JsonNode content);

        /**
         * Indents every line by one configured indentation unit.
         *
         * @param content content to indent
         * @return indented content
         */
        String indent(String content);
    }

    /** Converts one Tiptap JSON node into Markdown. */
    @FunctionalInterface
    public interface NodeRenderer {
        /**
         * Renders a matched Tiptap node.
         *
         * @param node JSON node whose {@code type} matched this registration
         * @param context recursive rendering and indentation helpers
         * @return Markdown representation; never {@code null}
         */
        String render(JsonNode node, RenderContext context);
    }

    private final Class<? extends Node> nodeClass;
    private final NodeParser parser;
    private final String nodeType;
    private final NodeRenderer renderer;

    private MarkdownExtension(Class<? extends Node> nodeClass, NodeParser parser,
                              String nodeType, NodeRenderer renderer) {
        this.nodeClass = nodeClass;
        this.parser = parser;
        this.nodeType = nodeType;
        this.renderer = renderer;
    }

    /**
     * Creates a parse-only registration.
     *
     * @param nodeClass flexmark AST class matched by this parser
     * @param parser callback that creates Tiptap JSON
     * @return immutable extension registration
     * @throws NullPointerException if either argument is {@code null}
     */
    public static MarkdownExtension parser(Class<? extends Node> nodeClass, NodeParser parser) {
        if (nodeClass == null || parser == null) {
            throw new NullPointerException("nodeClass and parser are required");
        }
        return new MarkdownExtension(nodeClass, parser, null, null);
    }

    /**
     * Creates a render-only registration.
     *
     * @param nodeType exact Tiptap JSON {@code type} handled by the renderer
     * @param renderer callback that creates Markdown
     * @return immutable extension registration
     * @throws NullPointerException if either argument is {@code null}
     */
    public static MarkdownExtension renderer(String nodeType, NodeRenderer renderer) {
        if (nodeType == null || renderer == null) {
            throw new NullPointerException("nodeType and renderer are required");
        }
        return new MarkdownExtension(null, null, nodeType, renderer);
    }

    /**
     * Creates a bidirectional parser and renderer registration.
     *
     * <p>The AST class and JSON node type do not need to share the same name;
     * the parser callback defines the JSON representation explicitly.</p>
     *
     * @param nodeClass flexmark AST class matched by the parser
     * @param parser callback that creates Tiptap JSON
     * @param nodeType exact Tiptap JSON {@code type} handled by the renderer
     * @param renderer callback that creates Markdown
     * @return immutable extension registration
     * @throws NullPointerException if any argument is {@code null}
     */
    public static MarkdownExtension of(Class<? extends Node> nodeClass, NodeParser parser,
                                       String nodeType, NodeRenderer renderer) {
        if (nodeClass == null || parser == null || nodeType == null || renderer == null) {
            throw new NullPointerException("all extension fields are required");
        }
        return new MarkdownExtension(nodeClass, parser, nodeType, renderer);
    }

    Class<? extends Node> getNodeClass() { return nodeClass; }
    NodeParser getParser() { return parser; }
    String getNodeType() { return nodeType; }
    NodeRenderer getRenderer() { return renderer; }
}

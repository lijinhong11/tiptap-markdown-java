package io.github.lijinhong11.tiptapmarkdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.ins.InsExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts Markdown to and from Tiptap's ProseMirror-compatible JSON format.
 *
 * <p>A manager is configured once and can then be reused for any number of
 * conversions:</p>
 *
 * <pre>{@code
 * MarkdownManager markdown = new MarkdownManager();
 * ObjectNode document = markdown.parse("# Welcome");
 * String source = markdown.serialize(document);
 * }</pre>
 *
 * <p>The generated JSON uses standard Tiptap node names such as
 * {@code doc}, {@code paragraph}, {@code heading}, {@code bulletList}, and
 * {@code text}. Inline formatting is represented by ProseMirror-compatible
 * {@code marks} arrays.</p>
 *
 * <p>This class has no mutable conversion state. A shared instance may be used
 * concurrently provided that a supplied {@link ObjectMapper} is fully
 * configured before the manager is created and is not reconfigured while the
 * manager is in use.</p>
 *
 * @see MarkdownOptions
 * @see MarkdownExtension
 */
public final class MarkdownManager {
    private final ObjectMapper objectMapper;
    private final Parser parser;
    private final MarkdownOptions options;

    /**
     * Creates a manager with a new {@link ObjectMapper} and the default
     * Markdown configuration.
     *
     * @see MarkdownOptions#defaults()
     */
    public MarkdownManager() {
        this(new ObjectMapper(), MarkdownOptions.defaults());
    }

    /**
     * Creates a manager that uses the supplied Jackson mapper and the default
     * Markdown configuration.
     *
     * <p>The mapper is retained rather than copied. Configure it completely
     * before constructing the manager.</p>
     *
     * @param objectMapper mapper used to create Tiptap JSON nodes
     * @throws NullPointerException if {@code objectMapper} is {@code null}
     * @see MarkdownOptions#defaults()
     */
    public MarkdownManager(ObjectMapper objectMapper) {
        this(objectMapper, MarkdownOptions.defaults());
    }

    /**
     * Creates a manager with a new {@link ObjectMapper} and the supplied
     * configuration.
     *
     * @param options immutable parser, renderer, and indentation configuration
     * @throws NullPointerException if {@code options} is {@code null}
     */
    public MarkdownManager(MarkdownOptions options) {
        this(new ObjectMapper(), options);
    }

    /**
     * Creates a fully configured manager.
     *
     * <p>Built-in flexmark extensions are registered first, followed by the
     * extensions supplied through {@link MarkdownOptions.Builder}. Custom
     * Tiptap handlers are consulted before the built-in node mappings.</p>
     *
     * @param objectMapper mapper used to create Tiptap JSON nodes
     * @param options immutable parser, renderer, and indentation configuration
     * @throws NullPointerException if either argument is {@code null}
     */
    public MarkdownManager(ObjectMapper objectMapper, MarkdownOptions options) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.options = Objects.requireNonNull(options, "options");

        MutableDataSet parserOptions = options.getFlexmarkOptions();
        List<com.vladsch.flexmark.util.misc.Extension> extensions = new ArrayList<com.vladsch.flexmark.util.misc.Extension>(Arrays.asList(
                AutolinkExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                InsExtension.create(),
                TablesExtension.create()
        ));
        extensions.addAll(options.getFlexmarkExtensions());
        parserOptions.set(Parser.EXTENSIONS, extensions);
        this.parser = Parser.builder(parserOptions).build();
    }

    /**
     * Parses Markdown into a Tiptap document node.
     *
     * <p>The returned object always has {@code "type": "doc"}. Its
     * {@code content} array contains the block nodes produced by flexmark and
     * any registered {@link MarkdownExtension.NodeParser node parsers}.</p>
     *
     * @param markdown Markdown source; may be empty but never {@code null}
     * @return a newly allocated Tiptap {@code doc} node
     * @throws NullPointerException if {@code markdown} is {@code null}
     */
    public ObjectNode parse(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        return new TiptapMarkdownParser(objectMapper, options.getMarkdownExtensions()).parse(parser.parse(markdown));
    }

    /**
     * Serializes Tiptap JSON to normalized Markdown.
     *
     * <p>The input may be a complete {@code doc} node, one content node, or an
     * array of content nodes. Markdown syntax is normalized during rendering;
     * for example, headings use ATX markers and horizontal rules use
     * {@code ---}.</p>
     *
     * @param document Tiptap document, node, or content array
     * @return rendered Markdown, or an empty string for empty content
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public String serialize(JsonNode document) {
        Objects.requireNonNull(document, "document");
        return new TiptapMarkdownSerializer(options).serialize(document);
    }

    /**
     * Returns the configured indentation character.
     *
     * @return either one space ({@code " "}) or one tab ({@code "\t"})
     * @see MarkdownOptions.Builder#indentation(MarkdownOptions.IndentationStyle, int)
     */
    public String getIndentCharacter() {
        return options.getIndentationStyle() == MarkdownOptions.IndentationStyle.SPACE ? " " : "\t";
    }

    /**
     * Returns one complete indentation unit.
     *
     * <p>The value consists of {@link #getIndentCharacter()} repeated by the
     * configured indentation size. The default is two spaces.</p>
     *
     * @return the non-empty indentation unit used for nested content
     */
    public String getIndentString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < options.getIndentationSize(); i++) {
            result.append(getIndentCharacter());
        }
        return result.toString();
    }
}

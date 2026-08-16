package io.github.lijinhong11.tiptapmarkdown;

import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable configuration for a {@link MarkdownManager}.
 *
 * <p>Instances are created with a fluent builder. Defaults use two-space
 * indentation and enable the manager's built-in Markdown support:</p>
 *
 * <pre>{@code
 * MarkdownOptions options = MarkdownOptions.builder()
 *     .indentation(MarkdownOptions.IndentationStyle.TAB, 1)
 *     .addFlexmarkExtension(MyFlexmarkExtension.create())
 *     .addExtension(myTiptapExtension)
 *     .build();
 *
 * MarkdownManager markdown = new MarkdownManager(objectMapper, options);
 * }</pre>
 *
 * <p>The builder defensively copies flexmark options and extension lists when
 * {@link Builder#build()} is called.</p>
 */
public final class MarkdownOptions {
    /** Selects the character used for each indentation unit. */
    public enum IndentationStyle {
        /** Indent nested content with spaces. */
        SPACE,
        /** Indent nested content with horizontal tab characters. */
        TAB
    }

    private final IndentationStyle indentationStyle;
    private final int indentationSize;
    private final MutableDataSet flexmarkOptions;
    private final List<Extension> flexmarkExtensions;
    private final List<MarkdownExtension> markdownExtensions;

    private MarkdownOptions(Builder builder) {
        indentationStyle = builder.indentationStyle;
        indentationSize = builder.indentationSize;
        flexmarkOptions = new MutableDataSet(builder.flexmarkOptions);
        flexmarkExtensions = Collections.unmodifiableList(new ArrayList<Extension>(builder.flexmarkExtensions));
        markdownExtensions = Collections.unmodifiableList(new ArrayList<MarkdownExtension>(builder.markdownExtensions));
    }

    /**
     * Creates an empty builder initialized with the documented defaults.
     *
     * @return a new independent builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the default configuration.
     *
     * @return a new immutable configuration using two-space indentation
     */
    public static MarkdownOptions defaults() {
        return builder().build();
    }

    IndentationStyle getIndentationStyle() {
        return indentationStyle;
    }

    int getIndentationSize() {
        return indentationSize;
    }

    MutableDataSet getFlexmarkOptions() {
        return new MutableDataSet(flexmarkOptions);
    }

    List<Extension> getFlexmarkExtensions() {
        return flexmarkExtensions;
    }

    List<MarkdownExtension> getMarkdownExtensions() {
        return markdownExtensions;
    }

    /**
     * Fluent builder for {@link MarkdownOptions}.
     *
     * <p>A builder is mutable and not thread-safe. The resulting
     * {@link MarkdownOptions} is immutable and does not retain mutable builder
     * collections.</p>
     */
    public static final class Builder {
        private IndentationStyle indentationStyle = IndentationStyle.SPACE;
        private int indentationSize = 2;
        private MutableDataSet flexmarkOptions = new MutableDataSet();
        private final List<Extension> flexmarkExtensions = new ArrayList<Extension>();
        private final List<MarkdownExtension> markdownExtensions = new ArrayList<MarkdownExtension>();

        /**
         * Configures the character and width of one indentation unit.
         *
         * <p>For {@link IndentationStyle#TAB}, {@code size} is the number of
         * tab characters, not a visual tab width.</p>
         *
         * @param style indentation character style
         * @param size number of characters in one indentation unit; at least 1
         * @return this builder
         * @throws NullPointerException if {@code style} is {@code null}
         * @throws IllegalArgumentException if {@code size} is less than 1
         */
        public Builder indentation(IndentationStyle style, int size) {
            if (style == null) {
                throw new NullPointerException("style");
            }
            if (size < 1) {
                throw new IllegalArgumentException("indentation size must be at least 1");
            }
            indentationStyle = style;
            indentationSize = size;
            return this;
        }

        /**
         * Replaces the base flexmark parser options.
         *
         * <p>The supplied data set is copied immediately. The manager adds its
         * built-in and user-supplied extensions to the copied options when it
         * creates the parser.</p>
         *
         * @param options flexmark parser options to copy
         * @return this builder
         * @throws NullPointerException if {@code options} is {@code null}
         */
        public Builder flexmarkOptions(MutableDataSet options) {
            if (options == null) {
                throw new NullPointerException("options");
            }
            flexmarkOptions = new MutableDataSet(options);
            return this;
        }

        /**
         * Appends a flexmark parser extension.
         *
         * <p>This controls AST production. Register a corresponding
         * {@link MarkdownExtension.NodeParser} when the extension introduces
         * an AST node that is not handled by the built-in converter.</p>
         *
         * @param extension flexmark extension to append
         * @return this builder
         * @throws NullPointerException if {@code extension} is {@code null}
         */
        public Builder addFlexmarkExtension(Extension extension) {
            if (extension == null) {
                throw new NullPointerException("extension");
            }
            flexmarkExtensions.add(extension);
            return this;
        }

        /**
         * Appends a custom Tiptap parser and/or renderer registration.
         *
         * <p>Registrations are evaluated in insertion order. For duplicate AST
         * classes or node types, the last registration replaces earlier ones.</p>
         *
         * @param extension custom conversion registration
         * @return this builder
         * @throws NullPointerException if {@code extension} is {@code null}
         */
        public Builder addExtension(MarkdownExtension extension) {
            if (extension == null) {
                throw new NullPointerException("extension");
            }
            markdownExtensions.add(extension);
            return this;
        }

        /**
         * Builds an immutable snapshot of the current configuration.
         *
         * @return a new configuration instance
         */
        public MarkdownOptions build() {
            return new MarkdownOptions(this);
        }
    }
}

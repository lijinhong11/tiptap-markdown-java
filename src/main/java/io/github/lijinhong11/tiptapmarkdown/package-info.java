/**
 * Bidirectional conversion between Markdown and Tiptap's
 * ProseMirror-compatible JSON document format.
 *
 * <p>The main entry point is
 * {@link io.github.lijinhong11.tiptapmarkdown.MarkdownManager}. It uses
 * flexmark-java for Markdown parsing and Jackson for the JSON tree model:</p>
 *
 * <pre>{@code
 * MarkdownManager markdown = new MarkdownManager();
 *
 * ObjectNode document = markdown.parse(
 *     "# Hello\n\nThis is **Markdown**."
 * );
 * String source = markdown.serialize(document);
 * }</pre>
 *
 * <p>Conversion can be configured through
 * {@link io.github.lijinhong11.tiptapmarkdown.MarkdownOptions}. Applications
 * with custom Tiptap nodes can register matching flexmark AST parsers and JSON
 * renderers through
 * {@link io.github.lijinhong11.tiptapmarkdown.MarkdownExtension}.</p>
 *
 * @see io.github.lijinhong11.tiptapmarkdown.MarkdownManager
 * @see io.github.lijinhong11.tiptapmarkdown.MarkdownOptions
 * @see io.github.lijinhong11.tiptapmarkdown.MarkdownExtension
 */
package io.github.lijinhong11.tiptapmarkdown;

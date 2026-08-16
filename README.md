# tiptap-markdown-java

Java 8 compatible Markdown conversion for Tiptap/ProseMirror JSON, powered by
flexmark-java.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lijinhong11/tiptap-markdown-java?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.lijinhong11/tiptap-markdown-java)

Use the latest version shown by the badge above.

### Gradle Kotlin DSL

```kotlin
dependencies {
    implementation("io.github.lijinhong11:tiptap-markdown-java:<version>")
}
```

### Gradle Groovy DSL

```groovy
dependencies {
    implementation 'io.github.lijinhong11:tiptap-markdown-java:<version>'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.lijinhong11</groupId>
    <artifactId>tiptap-markdown-java</artifactId>
    <version>VERSION</version>
</dependency>
```

## Usage

```java
MarkdownManager markdown = new MarkdownManager();

ObjectNode document = markdown.parse("# Hello\n\nThis is **Markdown**.");
String source = markdown.serialize(document);
```

An existing Jackson mapper can be supplied with `new MarkdownManager(objectMapper)`.

## Configuration

```java
MarkdownOptions options = MarkdownOptions.builder()
    .indentation(MarkdownOptions.IndentationStyle.TAB, 1)
    .addFlexmarkExtension(MyFlexmarkExtension.create())
    .addExtension(myTiptapExtension)
    .build();

MarkdownManager markdown = new MarkdownManager(objectMapper, options);
```

`MarkdownExtension` registers a parser for a flexmark AST node, a renderer for
a Tiptap JSON node type, or both. Parser contexts expose block and inline child
conversion; renderer contexts expose block/inline rendering and configured
indentation.

## Supported content

- Paragraphs, headings, blockquotes, horizontal rules and fenced/indented code blocks
- Bullet lists, ordered lists and GFM task lists
- Bold, italic, strike, underline (`++text++`), inline code and link marks
- Inline URLs, email links, reference links and HTML entities
- Images and hard breaks
- GFM tables
- Table column alignment
- Mixed bullet/task lists, split into schema-valid consecutive list nodes
- Inline and block HTML preserved as literal text

`parse` returns a standard Tiptap `doc` object. `serialize` accepts a `doc`, a
single node, or an array of content nodes. Unknown node and mark types are
rendered through their content when possible.

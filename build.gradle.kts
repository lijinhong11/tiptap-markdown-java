import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.lijinhong11"
version = project.findProperty("version") as String

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    api("com.vladsch.flexmark:flexmark-all:0.62.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    configure(
        JavaLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources(),
        ),
    )

    signAllPublications()
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)

    coordinates(
        groupId = project.group.toString(),
        artifactId = "tiptap-markdown-java",
        version = project.version.toString(),
    )

    pom {
        name.set("Tiptap Markdown Java")
        description.set("Bidirectional Markdown conversion for Tiptap/ProseMirror JSON, powered by flexmark-java.")
        inceptionYear.set("2026")
        url.set("https://github.com/lijinhong11/tiptap-markdown-java")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit/")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("lijinhong11")
                name.set("lijinhong11")
                url.set("https://github.com/lijinhong11")
            }
        }

        scm {
            url.set("https://github.com/lijinhong11/tiptap-markdown-java")
            connection.set("scm:git:git://github.com/lijinhong11/tiptap-markdown-java.git")
            developerConnection.set("scm:git:ssh://git@github.com/lijinhong11/tiptap-markdown-java.git")
        }
    }
}

signing {
    useGpgCmd()
}
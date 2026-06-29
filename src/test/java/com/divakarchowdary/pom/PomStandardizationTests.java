package com.divakarchowdary.pom;

import com.divakarchowdary.pom.maven.PomWalker;
import com.divakarchowdary.pom.model.PomUpdaterProperties;
import com.divakarchowdary.pom.model.RepoResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PomStandardizationTests {

    @TempDir
    File tempDir;

    @Test
    void pomWalker_updatesPropertyVersion() throws Exception {
        File pom = new File(tempDir, "pom.xml");
        Files.writeString(pom.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test</artifactId>
                <version>1.0.0</version>
                <properties>
                    <log4j2.version>2.20.0</log4j2.version>
                </properties>
            </project>
            """);

        PomWalker.UpdateResult result = PomWalker.updateAllProperties(
                pom,
                Map.of("log4j2.version", "2.23.1"),
                Map.of(),
                Map.of()
        );

        assertTrue(result.isChanged());
        assertEquals(1, result.getUpdated().size());
        assertEquals("2.20.0", result.getUpdated().get("log4j2.version").getOldVersion());
        assertEquals("2.23.1", result.getUpdated().get("log4j2.version").getNewVersion());

        String updatedPom = Files.readString(pom.toPath());
        assertTrue(updatedPom.contains("2.23.1"));
    }

    @Test
    void pomWalker_noChanges_whenVersionAlreadyCurrent() throws Exception {
        File pom = new File(tempDir, "pom.xml");
        Files.writeString(pom.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <properties>
                    <log4j2.version>2.23.1</log4j2.version>
                </properties>
            </project>
            """);

        PomWalker.UpdateResult result = PomWalker.updateAllProperties(
                pom, Map.of("log4j2.version", "2.23.1"), Map.of(), Map.of());

        assertFalse(result.isChanged());
        assertTrue(result.getUpdated().isEmpty());
    }

    @Test
    void pomWalker_createsNewProperty_whenMissing() throws Exception {
        File pom = new File(tempDir, "pom.xml");
        Files.writeString(pom.toPath(), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <properties>
                    <existing.prop>1.0</existing.prop>
                </properties>
            </project>
            """);

        PomWalker.UpdateResult result = PomWalker.updateAllProperties(
                pom, Map.of("new.version", "2.0.0"), Map.of(), Map.of());

        assertTrue(result.isChanged());
        assertTrue(result.getUpdated().containsKey("new.version"));
        assertEquals("(none)", result.getUpdated().get("new.version").getOldVersion());
    }

    @Test
    void repoResult_success_setsCorrectFields() {
        PomUpdaterProperties.Repo repo = new PomUpdaterProperties.Repo("test-repo");
        RepoResult result = RepoResult.success(repo, "https://github.com/org/test-repo/pull/42");

        assertEquals(RepoResult.Status.SUCCESS, result.getStatus());
        assertEquals("https://github.com/org/test-repo/pull/42", result.getPrUrl());
        assertNull(result.getErrorMessage());
    }

    @Test
    void repoResult_failed_setsErrorMessage() {
        PomUpdaterProperties.Repo repo = new PomUpdaterProperties.Repo("test-repo");
        RepoResult result = RepoResult.failed(repo, "Maven build failed");

        assertEquals(RepoResult.Status.FAILED, result.getStatus());
        assertEquals("Maven build failed", result.getErrorMessage());
        assertNull(result.getPrUrl());
    }

    @Test
    void repoResult_noChanges_hasCorrectStatus() {
        PomUpdaterProperties.Repo repo = new PomUpdaterProperties.Repo("test-repo");
        RepoResult result = RepoResult.noChanges(repo);

        assertEquals(RepoResult.Status.NO_CHANGES, result.getStatus());
        assertNull(result.getPrUrl());
        assertNull(result.getErrorMessage());
    }

    @Test
    void repoConfig_deserializes_withNoArgConstructor() {
        PomUpdaterProperties.Repo repo = new PomUpdaterProperties.Repo();
        repo.setName("my-service");
        assertEquals("my-service", repo.getName());
        assertEquals("my-service", repo.toString());
    }
}

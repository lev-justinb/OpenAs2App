package org.openas2.processor.receiver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DirectoryPollingModuleTest {

    @TempDir
    Path outboxDir;

    private DirectoryPollingModule poller;
    private boolean processCalled;

    @BeforeEach
    void setUp() {
        processCalled = false;
        poller = new AS2DirectoryPollingModule() {
            @Override
            protected void processSingleFile(File file, String fileEntryKey) {
                processCalled = true;
            }
        };
        poller.setOutboxDir(outboxDir.toString());
    }

    @Test
    public void triggerFileNow_returnsFalseWhenFileDoesNotExist() throws Exception {
        boolean result = poller.triggerFileNow("nonexistent.edi");
        assertFalse(result);
    }

    @Test
    public void triggerFileNow_returnsTrueAndInvokesProcessingWhenFileExists() throws Exception {
        File file = outboxDir.resolve("invoice.edi").toFile();
        file.createNewFile();

        boolean result = poller.triggerFileNow("invoice.edi");

        assertTrue(result);
        assertTrue(processCalled, "processSingleFile should have been called");
    }

    @Test
    public void triggerFileNow_returnsFalseWhenPollerIsBusy() throws Exception {
        File file = outboxDir.resolve("invoice.edi").toFile();
        file.createNewFile();

        poller.setBusy(true);
        boolean result = poller.triggerFileNow("invoice.edi");

        assertFalse(result);
        assertFalse(processCalled, "processSingleFile should NOT be called when busy");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    public void triggerFileNow_throwsWhenCanonicalPathEscapesOutboxViaSymlink() throws Exception {
        Path externalFile = Files.createTempFile("external", ".edi");
        externalFile.toFile().deleteOnExit();
        Path symlink = outboxDir.resolve("link.edi");
        Files.createSymbolicLink(symlink, externalFile);

        assertThrows(OpenAS2Exception.class, () -> poller.triggerFileNow("link.edi"));
    }
}

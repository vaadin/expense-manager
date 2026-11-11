package com.vaadin.expensemanager.base.ui.unified;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;
import com.vaadin.testing.unified.spi.UploadAccess;
import org.vaadin.addons.dramafinder.AutoWait;

/**
 * Project-owned Unified wrapper for {@link Upload}, kept beside the Core Set's
 * {@link com.vaadin.testing.unified.component.UnifiedUpload} (0.1.0-alpha2) for one
 * reason: on Playwright the Core upload waits until every entry of the upload's file
 * list is complete, and this app clears the list once the server has handled the
 * file, so the wait never ends (10 s timeout; parttio/unified-testing-tool#122).
 *
 * <p>Everywhere else it is the Core upload, through the same adapter SPI
 * ({@link UploadAccess}): Browserless drives the real {@code UploadHandler}, Selenium
 * TestBench's {@code UploadElement}. Playwright hands the bytes to the file
 * {@code <input>} and waits for the upload request's response instead, then for the
 * server to be idle again.
 */
@UnifiedComponent(value = Upload.class, overrides = @ToolImplementation(
        tool = TestingTool.PLAYWRIGHT, value = UnifiedUpload.Playwright.class))
public interface UnifiedUpload extends UnifiedWrapper, Findable<UnifiedUpload.UploadQuery> {

    interface UploadQuery extends Query<UnifiedUpload, UploadQuery> {}

    default void upload(String fileName, String mimeType, byte[] contents) {
        element().unwrap(UploadAccess.class).upload(fileName, mimeType, contents);
    }

    interface Playwright extends UnifiedUpload {
        @Override
        default void upload(String fileName, String mimeType, byte[] contents) {
            Locator upload = element().unwrap(Locator.class);
            // Wait for the upload request itself: the app may clear the file list once
            // it is handled, so a finished <vaadin-upload-file> is not a stable signal.
            upload.page().waitForResponse(
                    response -> "POST".equals(response.request().method())
                            && response.url().contains("/upload"),
                    () -> upload.locator("input[type=file]").first()
                            .setInputFiles(new FilePayload(fileName, mimeType, contents)));
            AutoWait.afterAction(upload.page());
        }
    }
}

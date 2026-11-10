package com.vaadin.expensemanager.base.ui.unified;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FilePayload;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadTester;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;
import org.vaadin.addons.dramafinder.AutoWait;

/**
 * Project-owned Unified wrapper for {@link Upload}, which the Unified API's Core Set
 * lacks (ADR-0027, proposed), and a {@link Scope} for its slotted button and icons.
 * Generic.
 *
 * <p>Uploading a file has no portable Unified Element operation, so both tools
 * override {@link #upload}: Browserless drives the component's real
 * {@code UploadHandler} through {@code UploadTester}; Playwright hands the bytes to
 * the file {@code <input>} (no temp file) and waits for the upload request's
 * response, then for the server to be idle again.
 */
@UnifiedComponent(value = Upload.class, overrides = {
        @ToolImplementation(tool = TestingTool.BROWSERLESS, value = UnifiedUpload.Browserless.class),
        @ToolImplementation(tool = TestingTool.PLAYWRIGHT, value = UnifiedUpload.Playwright.class) })
public interface UnifiedUpload extends UnifiedWrapper, Scope, Findable<UnifiedUpload.UploadQuery> {

    interface UploadQuery extends Query<UnifiedUpload, UploadQuery> {}

    /** Uploads one file as a user choosing it would. */
    default void upload(String fileName, String mimeType, byte[] contents) {
        throw new UnsupportedOperationException("UnifiedUpload.upload has no implementation for this Testing Tool");
    }

    /** Browserless: the tester feeds the bytes to the component's UploadHandler. */
    interface Browserless extends UnifiedUpload {
        @Override
        default void upload(String fileName, String mimeType, byte[] contents) {
            element().unwrap(UploadTester.class).upload(fileName, mimeType, contents);
        }
    }

    /** Playwright: the bytes go to the file input; then wait for the upload to settle. */
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

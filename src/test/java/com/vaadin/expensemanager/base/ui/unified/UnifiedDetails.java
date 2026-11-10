package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.flow.component.details.Details;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for {@link Details} — not in the Unified API's Core
 * Set, and the app's {@code ReportListSection} is one (ADR-0027, proposed).
 *
 * <p>In a browser the summary is a real {@code <vaadin-details-summary>} child and
 * clicking it is exactly what a user does. Browserless runs no client-side code, so
 * that click would toggle nothing: the override uses Browserless's own
 * {@code DetailsTester} instead.
 */
@UnifiedComponent(value = Details.class, overrides = @ToolImplementation(
        tool = TestingTool.BROWSERLESS, value = UnifiedDetails.Browserless.class))
public interface UnifiedDetails extends UnifiedWrapper, Findable<UnifiedDetails.DetailsQuery> {

    interface DetailsQuery extends Query<UnifiedDetails, DetailsQuery> {}

    /** Opens or closes the section by its summary, as a user does. */
    default void toggle() {
        element().findChildren("vaadin-details-summary").getFirst().click();
    }

    default boolean isOpen() {
        return Boolean.TRUE.equals(element().getPropertyBoolean("opened"));
    }

    /** The Browserless implementation: the tester, since there is no client. */
    interface Browserless extends UnifiedDetails {
        @Override
        default void toggle() {
            element().unwrap(com.vaadin.flow.component.details.DetailsTester.class)
                    .toggleDetails();
        }
    }
}

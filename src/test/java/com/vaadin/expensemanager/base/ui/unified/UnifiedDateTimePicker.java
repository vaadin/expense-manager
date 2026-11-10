package com.vaadin.expensemanager.base.ui.unified;

import java.time.LocalDateTime;

import com.microsoft.playwright.Locator;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.component.UnifiedField;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.Query;
import org.vaadin.addons.dramafinder.AutoWait;
import org.vaadin.addons.dramafinder.element.DateTimePickerElement;

/**
 * Project-owned Unified wrapper for {@link DateTimePicker}, which the Unified API's
 * Core Set lacks (ADR-0027, proposed). Generic.
 *
 * <p>Two per-tool overrides, because the value is two inputs in a browser:
 * <ul>
 *   <li><b>Playwright</b> — the adapter's generic "fill the first input" would type the
 *       whole value into the date half; Dramafinder's {@link DateTimePickerElement}
 *       types the date and the time into their own inputs, as a user does.</li>
 *   <li><b>Browserless</b> — {@code DateTimePickerTester} is the "user" and refuses a
 *       value outside {@code min}/{@code max}, which a user <em>can</em> still type by
 *       hand. {@link #typeOutOfRange} sets it on the component instead, the way that
 *       hand-typed (or crafted) input reaches the server.</li>
 * </ul>
 * {@code min}/{@code max} are real properties, ISO-formatted, on both tools.
 */
@UnifiedComponent(value = DateTimePicker.class, overrides = {
        @ToolImplementation(tool = TestingTool.PLAYWRIGHT,
                value = UnifiedDateTimePicker.Playwright.class),
        @ToolImplementation(tool = TestingTool.BROWSERLESS,
                value = UnifiedDateTimePicker.Browserless.class) })
public interface UnifiedDateTimePicker extends UnifiedField,
        Findable<UnifiedDateTimePicker.DateTimePickerQuery> {

    interface DateTimePickerQuery extends Query<UnifiedDateTimePicker, DateTimePickerQuery>,
            LabelFilter<DateTimePickerQuery> {}

    /** Enters the date and time as a user would. */
    default void setValue(LocalDateTime value) { element().setValueAsUser(value); }

    /**
     * Enters a value the picker's {@code min}/{@code max} would not offer from its
     * overlay — typed by hand, past the constraint — so a test can prove validation,
     * not the widget, is what refuses it.
     */
    default void typeOutOfRange(LocalDateTime value) { setValue(value); }

    default LocalDateTime getValue() { return parse(element().getProperty("value")); }

    default LocalDateTime getMin() { return parse(element().getProperty("min")); }

    default LocalDateTime getMax() { return parse(element().getProperty("max")); }

    private static LocalDateTime parse(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null
                : LocalDateTime.parse(value);
    }

    /** Playwright: date and time typed into their own inputs. */
    interface Playwright extends UnifiedDateTimePicker {
        @Override
        default void setValue(LocalDateTime value) {
            var locator = element().unwrap(Locator.class);
            new DateTimePickerElement(locator).setValue(value);
            AutoWait.afterAction(locator.page());
        }
    }

    /** Browserless: an out-of-range value goes straight to the component. */
    interface Browserless extends UnifiedDateTimePicker {
        @Override
        default void typeOutOfRange(LocalDateTime value) {
            element().unwrap(DateTimePicker.class).setValue(value);
        }
    }
}

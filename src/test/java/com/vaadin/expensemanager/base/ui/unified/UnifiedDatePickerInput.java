package com.vaadin.expensemanager.base.ui.unified;

import com.microsoft.playwright.Locator;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.component.UnifiedField;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.Query;
import org.vaadin.addons.dramafinder.AutoWait;

/**
 * Project-owned complement to the Core Set's {@code UnifiedDatePicker} (ADR-0027,
 * proposed): typing <em>raw text</em> into a {@link DatePicker}, including text that
 * does not parse as a date. The Core Set's {@code setValue(LocalDate)} cannot express
 * that, and it is how a user reaches the bad-input state. Generic.
 *
 * <p>Per tool, because the bad-input state is negotiated by the web component:
 * <ul>
 *   <li><b>Playwright</b> (default path through the Escape Hatch) types into the input
 *       and commits with Enter; the web component fires {@code unparsable-change}.</li>
 *   <li><b>Browserless</b> has no web component, and {@code DatePickerTester} refuses
 *       anything but a {@code LocalDate} (F-021). The override plays the web
 *       component's part of the protocol: the {@code _inputElementValue} property the
 *       server reads, then the {@code unparsable-change} event it listens for.</li>
 * </ul>
 */
@UnifiedComponent(value = DatePicker.class, overrides = {
        @ToolImplementation(tool = TestingTool.BROWSERLESS,
                value = UnifiedDatePickerInput.Browserless.class),
        @ToolImplementation(tool = TestingTool.PLAYWRIGHT,
                value = UnifiedDatePickerInput.Playwright.class) })
public interface UnifiedDatePickerInput extends UnifiedField,
        Findable<UnifiedDatePickerInput.DatePickerInputQuery> {

    interface DatePickerInputQuery extends Query<UnifiedDatePickerInput, DatePickerInputQuery>,
            LabelFilter<DatePickerInputQuery> {}

    /** Types {@code text} into the field and commits it, parseable or not. */
    default void typeText(String text) {
        throw new UnsupportedOperationException(
                "UnifiedDatePickerInput.typeText has no implementation for this Testing Tool");
    }

    interface Playwright extends UnifiedDatePickerInput {
        @Override
        default void typeText(String text) {
            Locator input = element().unwrap(Locator.class).locator("input").first();
            input.fill(text);
            input.press("Enter");
            AutoWait.afterAction(input.page());
        }
    }

    interface Browserless extends UnifiedDatePickerInput {
        @Override
        default void typeText(String text) {
            element().setProperty("_inputElementValue", text);
            element().dispatchEvent("unparsable-change");
        }
    }
}

package com.vaadin.expensemanager.base.ui.unified;

import java.math.BigDecimal;

import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.component.UnifiedField;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for {@link BigDecimalField} — not in the Unified
 * API's Core Set, which has only {@code UnifiedNumberField} (a {@code Double}
 * field). Generic: nothing here is specific to this app (ADR-0027, proposed).
 *
 * <p>{@link #setValue} goes through {@code setValueAsUser}: on Browserless that is
 * the field's tester with the {@code BigDecimal} as given; in a browser the value
 * is typed into the input as plain text ({@code 8.5}), as a user would.
 */
@UnifiedComponent(BigDecimalField.class)
public interface UnifiedBigDecimalField extends UnifiedField,
        Findable<UnifiedBigDecimalField.BigDecimalFieldQuery> {

    interface BigDecimalFieldQuery extends Query<UnifiedBigDecimalField, BigDecimalFieldQuery>,
            LabelFilter<BigDecimalFieldQuery>, PlaceholderFilter<BigDecimalFieldQuery> {}

    /** Types the value as a user would; {@code null} empties the field. */
    default void setValue(BigDecimal value) {
        element().setValueAsUser(value);
    }

    default void clear() {
        element().setValueAsUser(null);
    }

    /** The current value, or {@code null} when empty. Immediate; compare with {@code isEqualByComparingTo}. */
    default BigDecimal getValue() {
        String value = element().getProperty("value");
        return value == null || value.isBlank() || "null".equals(value)
                ? null : new BigDecimal(value);
    }
}

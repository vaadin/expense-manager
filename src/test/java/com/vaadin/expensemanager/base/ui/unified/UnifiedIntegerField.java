package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.component.UnifiedField;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for {@link IntegerField} — the Core Set has only
 * {@code UnifiedNumberField}, whose {@code Double} value an {@code IntegerField}'s
 * tester rejects (ADR-0027, proposed). Generic.
 */
@UnifiedComponent(IntegerField.class)
public interface UnifiedIntegerField extends UnifiedField,
        Findable<UnifiedIntegerField.IntegerFieldQuery> {

    interface IntegerFieldQuery extends Query<UnifiedIntegerField, IntegerFieldQuery>,
            LabelFilter<IntegerFieldQuery>, PlaceholderFilter<IntegerFieldQuery> {}

    /** Types the value as a user would; {@code null} empties the field. */
    default void setValue(Integer value) {
        element().setValueAsUser(value);
    }

    /** The value, or {@code null} when empty. Immediate. */
    default Integer getValue() {
        return integer(element().getProperty("value"));
    }

    /**
     * The {@code min} constraint, or {@code null} when unset. A real web component
     * property, so it reads the same on both tools — as {@code 0.0} on the server and
     * {@code 0} in a browser, hence the parse.
     */
    default Integer getMin() {
        return integer(element().getProperty("min"));
    }

    /** The {@code max} constraint, or {@code null} when unset. */
    default Integer getMax() {
        return integer(element().getProperty("max"));
    }

    private static Integer integer(String value) {
        return value == null || value.isBlank() || "null".equals(value)
                ? null : (int) Double.parseDouble(value);
    }
}

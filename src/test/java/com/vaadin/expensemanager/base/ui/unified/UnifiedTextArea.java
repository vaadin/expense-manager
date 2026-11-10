package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.component.UnifiedField;
import com.vaadin.testing.unified.query.LabelFilter;
import com.vaadin.testing.unified.query.PlaceholderFilter;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for {@link TextArea} — not in the Unified API's Core
 * Set (ADR-0027, proposed). Generic: the same shape as the Core Set's
 * {@code UnifiedTextField}, so it can be merged with any other copy or upstreamed.
 *
 * <p>No per-tool override is needed: {@code setValueAsUser} types into the
 * {@code <vaadin-text-area>} in a browser and goes through {@code TextAreaTester}
 * on Browserless.
 */
@UnifiedComponent(TextArea.class)
public interface UnifiedTextArea extends UnifiedField, Findable<UnifiedTextArea.TextAreaQuery> {

    interface TextAreaQuery extends Query<UnifiedTextArea, TextAreaQuery>,
            LabelFilter<TextAreaQuery>, PlaceholderFilter<TextAreaQuery> {}

    /** Types {@code value} as a user does (Auto-Wait). */
    default void setValue(String value) {
        element().setValueAsUser(value);
    }

    default void clear() {
        element().setValueAsUser("");
    }

    default String getValue() {
        return element().getProperty("value");
    }

    /** The {@code minRows} property: the rows the area shows at rest. */
    default Integer getMinRows() {
        return element().getPropertyInteger("minRows");
    }

    /** The {@code maxlength} property. */
    default Integer getMaxLength() {
        return element().getPropertyInteger("maxlength");
    }
}

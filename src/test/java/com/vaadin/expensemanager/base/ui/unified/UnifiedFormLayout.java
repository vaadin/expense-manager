package com.vaadin.expensemanager.base.ui.unified;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for {@link FormLayout}, which the Unified API's Core
 * Set lacks (ADR-0027, proposed), and a {@link Scope} for its fields. Generic.
 *
 * <p>Everything here is what the server sends the web component, so it reads on both
 * tools: the {@code responsiveSteps} property, the spacing custom properties in the
 * inline style, a child's {@code colspan} attribute.
 */
@UnifiedComponent(FormLayout.class)
public interface UnifiedFormLayout extends UnifiedWrapper, Scope,
        Findable<UnifiedFormLayout.FormLayoutQuery> {

    interface FormLayoutQuery extends Query<UnifiedFormLayout, FormLayoutQuery> {}

    /** {@code minWidth} and {@code columns} of one step, in either tool's rendering. */
    Pattern STEP = Pattern.compile("minWidth\\W*([^,\"}\\s]+)[^}]*?columns\\W*(\\d+)");

    /**
     * The responsive steps as {@code "minWidth:columns"}, e.g. {@code ["0:1", "24rem:2"]}.
     * The property is JSON on the server and a JS array in a browser, so both are read
     * by pattern rather than compared as text.
     */
    default List<String> getResponsiveSteps() {
        String raw = element().getProperty("responsiveSteps");
        List<String> steps = new ArrayList<>();
        if (raw != null) {
            var matcher = STEP.matcher(raw);
            while (matcher.find()) {
                steps.add(matcher.group(1) + ":" + matcher.group(2));
            }
        }
        return steps;
    }

    default String getColumnSpacing() {
        return UnifiedElements.style(element()).get("--vaadin-form-layout-column-spacing");
    }

    default String getRowSpacing() {
        return UnifiedElements.style(element()).get("--vaadin-form-layout-row-spacing");
    }

    /** The layout's direct children, in order. */
    default List<UnifiedElement> getChildren() {
        return element().children();
    }

    /** How many columns a child spans: its {@code colspan} attribute, 1 when unset. */
    static int colspanOf(UnifiedElement child) {
        String colspan = child.getAttribute("colspan");
        return colspan == null ? 1 : Integer.parseInt(colspan);
    }
}

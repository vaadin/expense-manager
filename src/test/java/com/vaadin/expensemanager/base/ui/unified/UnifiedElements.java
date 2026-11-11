package com.vaadin.expensemanager.base.ui.unified;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.component.UnifiedTabs;

/**
 * Reads on a Unified Element that the Unified API does not offer but every Testing Tool
 * can answer (ADR-0027, proposed). Generic.
 */
public final class UnifiedElements {

    private UnifiedElements() {}

    /**
     * The inline style the server set, as {@code property -> value}. Both tools expose it
     * as the {@code style} attribute, but serialise it differently ({@code a:b;} on the
     * server, {@code a: b;} in a browser), so it is parsed rather than compared.
     */
    public static Map<String, String> style(UnifiedElement element) {
        Map<String, String> style = new LinkedHashMap<>();
        String attribute = element.getAttribute("style");
        if (attribute == null) {
            return style;
        }
        for (String declaration : attribute.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                style.put(declaration.substring(0, colon).trim(),
                        declaration.substring(colon + 1).trim());
            }
        }
        return style;
    }

    /**
     * The text of the selected tab, or {@code null} when none is.
     *
     * <p>Not {@link UnifiedTabs#getSelectedTab()}: on Browserless (0.1.0-alpha2) that
     * reads {@code Tab#getLabel()}, which is empty for a tab that wraps a component,
     * as every tab of the app's {@code ReferenceTabs} wraps a {@code RouterLink}. The
     * bar's {@code selected} index and the tabs' text read the same on every tool.
     * Upstream: parttio/unified-testing-tool#107.
     */
    public static String selectedTabText(UnifiedTabs tabs) {
        Integer selected = tabs.element().getPropertyInteger("selected");
        if (selected == null || selected < 0) {
            return null;
        }
        return tabs.element().findChildren("vaadin-tab").get(selected).getText();
    }

    /** Whether the {@code class} attribute carries {@code className}. */
    public static boolean hasClassName(UnifiedElement element, String className) {
        String classes = element.getAttribute("class");
        return classes != null && List.of(classes.trim().split("\\s+")).contains(className);
    }

    /** The class names on the element. */
    public static List<String> classNames(UnifiedElement element) {
        String classes = element.getAttribute("class");
        return classes == null || classes.isBlank() ? List.of()
                : List.of(classes.trim().split("\\s+"));
    }
}

package com.vaadin.expensemanager.base.ui.unified;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.testing.unified.UnifiedElement;

/**
 * Reads on a Unified Element that the Unified API does not offer but every Testing Tool
 * can answer (ADR-0027, proposed). Generic.
 */
public final class UnifiedElements {

    private UnifiedElements() {}

    /**
     * The element's direct children, in document order.
     *
     * <p>{@link UnifiedElement#findChildren} means different things per tool: the
     * element's direct children of one tag on Browserless, every <em>descendant</em>
     * matching a selector in a browser. So a browser asks for {@code xpath=./*} — not
     * {@code :scope > *}, which Playwright's CSS engine lets pierce into the element's
     * shadow root, adding e.g. a form layout's internal {@code <div id="layout">},
     * and Browserless — recognised by being able to hand over the server-side component
     * — asks once per child tag and keeps the order the component tree gives. Text
     * nodes are not children here, as in a browser's {@code ./*}; an element
     * mixing text and elements can't be listed on Browserless (its {@code findChildren}
     * fails on the text node), which this reports rather than crashes on.
     */
    public static List<UnifiedElement> children(UnifiedElement element) {
        var component = element.tryUnwrap(Component.class);
        if (component.isEmpty()) {
            return element.findChildren("xpath=./*");
        }
        var server = component.get().getElement();
        if (server.getChildren().anyMatch(child -> child.isTextNode())
                && server.getChildren().anyMatch(child -> !child.isTextNode())) {
            // Browserless's findChildren calls getTag() on every child, and a text
            // node's throws UnsupportedOperationException (alpha1).
            throw new IllegalStateException("Can't list the children of <"
                    + server.getTag() + ">: it mixes text and elements, which the"
                    + " Browserless adapter's findChildren cannot walk");
        }
        List<UnifiedElement> ordered = new ArrayList<>();
        Map<String, List<UnifiedElement>> byTag = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        component.get().getElement().getChildren().filter(child -> !child.isTextNode())
                .forEach(child -> {
            String tag = child.getTag();
            var sameTag = byTag.computeIfAbsent(tag, element::findChildren);
            int index = used.merge(tag, 1, Integer::sum) - 1;
            ordered.add(sameTag.get(index));
        });
        return ordered;
    }

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

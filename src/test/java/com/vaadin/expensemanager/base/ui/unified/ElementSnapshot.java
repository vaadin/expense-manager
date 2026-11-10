package com.vaadin.expensemanager.base.ui.unified;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.Locator;
import com.vaadin.flow.dom.Element;
import com.vaadin.testing.unified.UnifiedWrapper;

/**
 * A read-only copy of one element and its <em>direct</em> child elements, a few
 * levels deep: tag, class names and inline style — the structure a layout API
 * produces (ADR-0027, proposed).
 *
 * <p>The Unified API has no portable "children in order": {@code findChildren(tag)}
 * means direct children on Browserless and every descendant in a browser, and takes
 * one tag. This helper reads the real thing through each tool's Escape Hatch — the
 * server-side {@link Element} tree on Browserless, the DOM through the Playwright
 * {@link Locator} in a browser — so a test can assert anatomy ("a title row, then the
 * intro") and the {@code gap} a {@code setSpacing(String)} wrote, the same way on
 * both. Text nodes are not elements and are skipped on both.
 */
public record ElementSnapshot(String tag, List<String> classNames, Map<String, String> style,
        List<ElementSnapshot> children) {

    /** {@code wrapper}'s element and {@code depth} levels of child elements below it. */
    public static ElementSnapshot of(UnifiedWrapper wrapper, int depth) {
        var element = wrapper.element();
        var server = element.tryUnwrap(Element.class);
        if (server.isPresent()) {
            return of(server.get(), depth);
        }
        var locator = element.tryUnwrap(Locator.class);
        if (locator.isPresent()) {
            return fromBrowser(locator.get().evaluate(SNAPSHOT_JS, depth));
        }
        throw new UnsupportedOperationException(
                "ElementSnapshot supports the Browserless and Playwright Testing Tools");
    }

    /** The {@code gap} the element's inline style sets, or {@code null}. */
    public String gap() {
        return style.get("gap");
    }

    public ElementSnapshot child(int index) {
        return children.get(index);
    }

    public boolean hasClassName(String className) {
        return classNames.contains(className);
    }

    private static ElementSnapshot of(Element element, int depth) {
        var style = new LinkedHashMap<String, String>();
        element.getStyle().getNames().forEach(name -> style.put(name, element.getStyle().get(name)));
        var children = depth <= 0 ? List.<ElementSnapshot>of() : element.getChildren()
                .filter(child -> !child.isTextNode())
                .map(child -> of(child, depth - 1))
                .toList();
        return new ElementSnapshot(element.getTag(), List.copyOf(element.getClassList()),
                Map.copyOf(style), children);
    }

    private static final String SNAPSHOT_JS = """
            (root, depth) => {
              const snap = (e, d) => ({
                tag: e.localName,
                classNames: [...e.classList],
                style: e.style.cssText,
                children: d <= 0 ? [] : [...e.children].map(c => snap(c, d - 1))
              });
              return snap(root, depth);
            }""";

    /**
     * {@code cssText}, not the indexed longhands: a shorthand set through a
     * {@code var()} — {@code gap: var(--x)} — leaves {@code row-gap} and
     * {@code column-gap} empty (pending substitution), and only {@code cssText} keeps
     * the declaration as written. Values containing {@code ;} are not supported.
     */
    private static Map<String, String> parseCssText(String cssText) {
        var style = new LinkedHashMap<String, String>();
        for (String declaration : cssText.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                style.put(declaration.substring(0, colon).trim(),
                        declaration.substring(colon + 1).trim());
            }
        }
        return style;
    }

    @SuppressWarnings("unchecked")
    private static ElementSnapshot fromBrowser(Object raw) {
        var map = (Map<String, Object>) raw;
        var children = new ArrayList<ElementSnapshot>();
        for (Object child : (List<Object>) map.get("children")) {
            children.add(fromBrowser(child));
        }
        var style = parseCssText((String) map.get("style"));
        return new ElementSnapshot((String) map.get("tag"),
                ((List<Object>) map.get("classNames")).stream().map(String::valueOf).toList(),
                Map.copyOf(style), List.copyOf(children));
    }
}

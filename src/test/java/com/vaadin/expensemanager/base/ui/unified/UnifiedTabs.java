package com.vaadin.expensemanager.base.ui.unified;

import java.util.List;

import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for a bare {@link Tabs} bar — the Core Set has only
 * {@code UnifiedTabSheet}, which is tabs <em>plus</em> a content area. Generic:
 * nothing here is specific to this app (ADR-0027, proposed); the app's
 * {@code ReferenceTabs} is one, whose tabs are route links. A {@link Scope}, so a
 * test can reach what the tabs hold.
 *
 * <p>Selection is read from the {@code selected} index property, which Flow
 * synchronises, so both tools read the same number ({@code -1}: none). A tab's
 * label is its text: in a browser that is {@code innerText}, so a
 * {@code text-transform} on the tab would change its case (F-086); the app's tabs
 * have none.
 *
 * <p>Tabs are read with {@code findChildren("vaadin-tab")}, which is direct children
 * on Browserless and all descendants on Playwright (F-089). The two agree here only
 * because {@code <vaadin-tab>} never nests.
 */
@UnifiedComponent(Tabs.class)
public interface UnifiedTabs extends UnifiedWrapper, Scope, Findable<UnifiedTabs.TabsQuery> {

    interface TabsQuery extends Query<UnifiedTabs, TabsQuery> {}

    /** The tab labels, in bar order. Immediate. */
    default List<String> getTabTexts() {
        return element().findChildren("vaadin-tab").stream()
                .map(UnifiedElement::getText)
                .map(String::strip)
                .toList();
    }

    /** The selected tab's index; {@code -1} when none is. Immediate. */
    default int getSelectedIndex() {
        Integer selected = element().getPropertyInteger("selected");
        return selected == null ? -1 : selected;
    }

    /** The selected tab's label, or {@code null} when none is selected. Immediate. */
    default String getSelectedTabText() {
        int index = getSelectedIndex();
        return index < 0 ? null : getTabTexts().get(index);
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Wrapper (Unified Test ADR 0003) for a {@link VerticalLayout}, including the views
 * and blocks that extend it, for structure checks the Core Set has no Wrapper for.
 */
@UnifiedComponent(VerticalLayout.class)
public interface UnifiedVerticalLayout extends UnifiedWrapper, Scope, CommonFilters<UnifiedVerticalLayout> {

    /** The gap the layout API set ({@code setSpacing(String)} writes the inline {@code gap} style). */
    default String getGap() {
        return element().getInlineStyle("gap");
    }
}

package com.vaadin.expensemanager.base.ui;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.TextFilter;

/**
 * Wrapper (Unified Test ADR 0003) for a {@link HorizontalLayout}, including the rows
 * and bars that extend or are built from it, for structure checks the Core Set has
 * no Wrapper for.
 */
@UnifiedComponent(HorizontalLayout.class)
public interface UnifiedHorizontalLayout extends UnifiedWrapper, Scope,
        CommonFilters<UnifiedHorizontalLayout>, TextFilter<UnifiedHorizontalLayout> {

    default String getText() {
        return element().getText();
    }
}

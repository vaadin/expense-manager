package com.vaadin.expensemanager.base.ui;

import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Wrapper (Unified Test ADR 0003) for {@link PageHeader}, the shared title block of
 * the reference routes. A browser sees only a {@code <vaadin-vertical-layout>}, so
 * the {@code page-header} class tells it apart.
 */
@UnifiedComponent(value = PageHeader.class, tag = "vaadin-vertical-layout", selector = ".page-header",
        kind = "page header")
public interface UnifiedPageHeader extends UnifiedWrapper, Scope, CommonFilters<UnifiedPageHeader> {

    /** The gap the layout API set ({@code setSpacing(String)} writes the inline {@code gap} style). */
    default String getGap() {
        return element().getInlineStyle("gap");
    }
}

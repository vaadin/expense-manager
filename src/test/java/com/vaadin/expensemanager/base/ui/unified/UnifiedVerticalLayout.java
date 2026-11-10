package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for a {@link VerticalLayout} (or any subclass), and a
 * {@link Scope} for what is inside it (ADR-0027, proposed). Generic.
 *
 * <p>Same reason as {@link UnifiedDiv}: a tag-specific scope, so that
 * {@code withClassName} only visits {@code <vaadin-vertical-layout>}s on Playwright
 * (F-087). Every view and most blocks in a Flow app are vertical layouts, so always
 * narrow it with {@code .withClassName(..)} (F-088).
 */
@UnifiedComponent(VerticalLayout.class)
public interface UnifiedVerticalLayout extends UnifiedWrapper, Scope,
        Findable<UnifiedVerticalLayout.VerticalLayoutQuery> {

    interface VerticalLayoutQuery extends Query<UnifiedVerticalLayout, VerticalLayoutQuery> {}
}

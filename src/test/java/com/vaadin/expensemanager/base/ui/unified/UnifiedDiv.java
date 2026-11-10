package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.flow.component.html.Div;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;
import com.vaadin.testing.unified.query.TextFilter;

/**
 * Project-owned Unified wrapper for {@link Div} (ADR-0027, proposed): a {@link Scope},
 * so a test can search <em>inside</em> one — {@code findComponent(..)} returns a wrapper
 * that is not a Scope — and query by its real tag rather than {@code UnifiedContainer}'s
 * {@code *}, which on Playwright evaluates every element on the page (F-087). Generic.
 *
 * <p>Usually narrowed with {@code withClassName(..)}: the tag alone matches every
 * Div on the page.
 */
@UnifiedComponent(Div.class)
public interface UnifiedDiv extends UnifiedWrapper, Scope, Findable<UnifiedDiv.DivQuery> {

    interface DivQuery extends Query<UnifiedDiv, DivQuery>, TextFilter<DivQuery> {}

    default String getText() { return element().getText(); }
}

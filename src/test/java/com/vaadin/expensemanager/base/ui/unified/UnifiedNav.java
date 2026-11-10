package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.flow.component.html.Nav;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for a {@code <nav>} landmark ({@link Nav}), and a
 * {@link Scope} for what is inside it (ADR-0027, proposed). Generic.
 *
 * <p>Find it by its accessible name — a page with several {@code <nav>}s must label
 * them anyway: {@code page.find(UnifiedNav.class).withAccessibleName("Main")}.
 * Searching inside it with {@code UnifiedContainer} stays cheap on Playwright (F-087)
 * because the scope is one small subtree, not the page.
 */
@UnifiedComponent(Nav.class)
public interface UnifiedNav extends UnifiedWrapper, Scope, Findable<UnifiedNav.NavQuery> {

    interface NavQuery extends Query<UnifiedNav, NavQuery> {}
}

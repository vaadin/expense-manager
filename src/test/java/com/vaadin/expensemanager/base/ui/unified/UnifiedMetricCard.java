package com.vaadin.expensemanager.base.ui.unified;

import com.vaadin.expensemanager.base.ui.MetricCard;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.Scope;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for the app's {@link MetricCard}
 * ({@code docs/design/components/metric-card.md}), and a {@link Scope} for what is
 * inside it (ADR-0027, proposed).
 *
 * <p>A {@code MetricCard} is a {@code VerticalLayout}, so in a browser this matches
 * every {@code <vaadin-vertical-layout>} until a filter narrows it: query with
 * {@code .withClassName("metric-card")}. The Unified API gives a wrapper no way to
 * carry that filter itself.
 */
@UnifiedComponent(MetricCard.class)
public interface UnifiedMetricCard extends UnifiedWrapper, Scope,
        Findable<UnifiedMetricCard.MetricCardQuery> {

    interface MetricCardQuery extends Query<UnifiedMetricCard, MetricCardQuery> {}

    default String getText() {
        return element().getText();
    }
}

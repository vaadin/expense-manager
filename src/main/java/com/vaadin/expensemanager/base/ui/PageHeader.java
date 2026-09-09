package com.vaadin.expensemanager.base.ui;

import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * The block that opens a full-page view — the page's name at the display size,
 * the page's one primary action opposite it on the same line, and a paragraph of
 * standing explanation underneath
 * ({@code docs/design/components/page-header.md}).
 *
 * <p>Composed here once rather than per view: the three reference routes
 * ({@code /vat-rates}, {@code /expense-types}, {@code /allowance-rates}) each
 * built their own and had already drifted apart on the gap below the title
 * (#184).
 *
 * <p><strong>{@code H2}, not {@code H1}</strong> — the shell owns the page's
 * {@code h1} ({@link AppHeader}), so a view's own heading is the second level.
 * The {@code page-title} class carries the 40px size, not the element.
 *
 * <p><strong>The intro is not optional decoration.</strong> On the reference
 * views it is where the consequences live — that deactivating keeps history,
 * that a year's rates never change retroactively — and it is the only place a
 * first-time admin learns the rule before acting. Hence the required argument
 * and the readable 16px in {@code .page-intro}, rather than body size.
 *
 * <p>Nothing here is a disabled state: a user who may not create renders
 * <em>no</em> action rather than a greyed one (ADR-0008's route-level rule), so
 * the caller passes {@code null} instead.
 */
public class PageHeader extends VerticalLayout {

    /** A header with no primary action — {@code /allowance-rates}, whose actions live in its toolbar. */
    public PageHeader(String title, String intro) {
        this(title, intro, null);
    }

    /**
     * @param title  the page's name, rendered as the {@code h2}
     * @param intro  the standing explanation under it; required
     * @param action the page's one primary action, drawn opposite the title, or
     *               {@code null} when the page has none
     */
    public PageHeader(String title, String intro, Component action) {
        Objects.requireNonNull(intro, "the page intro carries the view's rules and is required");

        addClassName("page-header");
        setPadding(false);
        // The design's 20px between the title row and the intro. Spacing a
        // layout API can express never becomes CSS (docs/theming-layouts.md).
        setSpacing("var(--em-card-padding)");
        setWidthFull();

        var heading = new H2(title);
        heading.addClassName("page-title");

        var top = new HorizontalLayout(heading);
        top.addClassName("page-header-top");
        top.setWidthFull();
        top.setPadding(false);
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        top.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        if (action != null) {
            top.add(action);
        }

        var paragraph = new Paragraph(intro);
        paragraph.addClassName("page-intro");

        add(top, paragraph);
    }
}

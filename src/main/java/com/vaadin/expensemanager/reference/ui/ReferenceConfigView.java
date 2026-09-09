package com.vaadin.expensemanager.reference.ui;

import java.util.List;

import com.vaadin.expensemanager.base.ui.LucideIcon;
import com.vaadin.expensemanager.base.ui.PageHeader;
import com.vaadin.expensemanager.base.ui.ReferenceTabs;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.AbstractIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;

/**
 * Abstract base for the two ADMIN reference-data screens ({@link VatRateView},
 * {@link ExpenseTypeView}) — the shape they share: the shared
 * {@link PageHeader} block (heading, intro, and the primary create button that
 * opens the editor) and a {@link #grid}. Row-action buttons that both screens
 * use identically — the accessible icon button, the boundary-disabled reorder
 * buttons, and the text-status active toggle (ADR-0020) — are provided as
 * {@code protected} helpers.
 *
 * <p>Both screens carry the shared {@link ReferenceTabs} bar above the heading —
 * the sub-navigation across the three reference routes, which replaced the
 * shell's Reference Tables menu in #169. It is added here rather than per
 * subclass because it is identical on both, and its access filtering is what
 * keeps a route the user cannot reach out of the bar.
 *
 * <p>Each subclass configures the grid's columns and its actions cell with the
 * plain Vaadin API and implements the two kind-specific hooks: {@link #fetchItems}
 * (the display-ordered rows) and {@link #openEditor} (build the form + an
 * {@link com.vaadin.expensemanager.base.ui.EditorDialog}). No configuration
 * object — the subclass owns its grid.
 *
 * @param <T> the grid row (DTO) type
 */
abstract class ReferenceConfigView<T> extends VerticalLayout {

    /**
     * The create button's label on <strong>both</strong> config views.
     *
     * <p>Bare "New", per the frames: the {@code h2} beside it carries the noun,
     * which is the same reasoning as the bare "Add" next to "Foreign Per Diem"
     * (#169). Whether the app converges on "New" or "Add" for record creation is
     * a separate, open question; when it lands, this one constant flips.
     */
    private static final String CREATE_LABEL = "New";

    /** The screen's grid; the subclass adds its columns and actions cell. */
    protected final Grid<T> grid = new Grid<>();

    private List<T> items = List.of();

    protected ReferenceConfigView(String heading, String intro,
            AuthenticationContext authenticationContext) {
        setPadding(true);
        // The design's rhythm between the tab bar, the header block and the grid
        // (frame 143:1781 draws 40 between each).
        setSpacing("var(--em-section-gap)");

        // getClass(), not a per-subclass constant: the tab that must render
        // selected is the one for whichever concrete view is being constructed.
        add(new ReferenceTabs(selfType(), authenticationContext));

        var addButton = new Button(CREATE_LABEL, LucideIcon.PLUS.create(),
                event -> openEditor(null));
        addButton.addThemeVariants(ButtonVariant.PRIMARY);

        add(new PageHeader(heading, intro, addButton));

        grid.setAllRowsVisible(true);
        add(grid);
    }

    /**
     * This view's own class, for the tab bar's selected entry.
     *
     * <p>Not {@code getClass()} straight through: Spring method security proxies
     * every {@code @RolesAllowed} view, so at runtime that is
     * {@code VatRateView$$SpringCGLIB$$0} — which {@code ReferenceTabs} matches
     * by assignability anyway (F-070), but naming the mechanism here is cheaper
     * than rediscovering it.
     */
    @SuppressWarnings("unchecked")
    private Class<? extends Component> selfType() {
        return (Class<? extends Component>) getClass();
    }

    /** The display-ordered rows to show (called on every {@link #refresh}). */
    protected abstract List<T> fetchItems();

    /** Opens the add ({@code existing == null}) or edit editor for a row. */
    protected abstract void openEditor(T existing);

    /** Reloads the grid from {@link #fetchItems} — call after any mutation. */
    protected void refresh() {
        items = fetchItems();
        grid.setItems(items);
    }

    /** The rows currently shown, in order — for reorder boundary checks. */
    protected List<T> currentItems() {
        return items;
    }

    /** Index of {@code item} among {@link #currentItems} (rows carry a unique id). */
    protected int indexOf(T item) {
        return items.indexOf(item);
    }

    /**
     * An accessible tertiary icon button.
     *
     * <p>The icon arrives built rather than as a collection member: typed to
     * {@link LucideIcon} this base would pin both subclasses to one icon set, which
     * is exactly the coupling #163 removed. {@link AbstractIcon} is the common
     * supertype of every icon Vaadin has, so a subclass can pass whatever it needs
     * without this class knowing where glyphs come from.
     */
    protected Button iconButton(AbstractIcon<?> icon, String ariaLabel, Runnable action) {
        var button = new Button(icon, event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel(ariaLabel);
        return button;
    }

    /** A reorder icon button, disabled at the list boundary. */
    protected Button reorderButton(AbstractIcon<?> icon, String ariaLabel, boolean enabled,
            Runnable action) {
        var button = iconButton(icon, ariaLabel, action);
        button.setEnabled(enabled);
        return button;
    }

    /** The Activate/Deactivate toggle (deactivate never deletes, ADR-0018). */
    protected Button activeToggle(boolean active, String subject, Runnable action) {
        var button = new Button(active ? "Deactivate" : "Activate", event -> action.run());
        button.addThemeVariants(ButtonVariant.TERTIARY);
        button.setAriaLabel((active ? "Deactivate " : "Activate ") + subject);
        return button;
    }

    /** Text status, never colour alone (ADR-0020 no colour-only meaning). */
    protected static String statusLabel(boolean active) {
        return active ? "Active" : "Inactive";
    }
}

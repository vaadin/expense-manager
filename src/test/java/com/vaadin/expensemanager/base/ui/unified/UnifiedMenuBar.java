package com.vaadin.expensemanager.base.ui.unified;

import java.util.List;

import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarTester;
import com.vaadin.testing.unified.Findable;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.ToolImplementation;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.Query;

/**
 * Project-owned Unified wrapper for {@link MenuBar}; the app's {@code RowActionMenu}
 * is one (ADR-0027, proposed). Generic: nothing here knows about row actions.
 *
 * <p>Kept although 0.1.0-alpha2 adds a Core Set
 * {@link com.vaadin.testing.unified.component.UnifiedMenuBar}: that one addresses
 * every item by its text, and this app's triggers (the row "⋮" and the account
 * avatar) are icon-only, named by {@code aria-label} alone. It also cannot read an
 * item's enabled state, which the account menu's disabled user-name item needs.
 * Upstream: parttio/unified-testing-tool#108.
 *
 * <p>Root items are addressed by index, sub-menu items by the text the user reads:
 * a root item can be icon-only, so it may have no text to match.
 *
 * <p>In a browser the root items are {@code <vaadin-menu-bar-button>}s in the menu
 * bar's shadow root, and a sub-menu exists only while it is open: its items are
 * read by opening it, as a user does, and closing it again. Browserless has no
 * client to render either, so the override reads the server-side {@link MenuItem}s
 * and clicks through {@link MenuBarTester} — the tester on the instance, not
 * {@code use(menu)}, which cannot reach a menu inside a Grid cell (F-082).
 */
@UnifiedComponent(value = MenuBar.class, overrides = @ToolImplementation(
        tool = TestingTool.BROWSERLESS, value = UnifiedMenuBar.Browserless.class))
public interface UnifiedMenuBar extends UnifiedWrapper, Findable<UnifiedMenuBar.MenuBarQuery> {

    interface MenuBarQuery extends Query<UnifiedMenuBar, MenuBarQuery> {}

    /** One item of an open sub-menu, as the user sees it. */
    record Item(String text, boolean enabled) {}

    /** How long to wait for an opened sub-menu to render in a browser. */
    long SUB_MENU_WAIT_MILLIS = 5_000;

    /** The number of visible top-level items. */
    default int getRootItemCount() {
        return rootButtons(this).size();
    }

    /**
     * What a screen reader announces for a top-level item: its {@code aria-label},
     * or — for Flow's, which sits on the item component inside the button — that
     * component's, else its text.
     */
    default String getRootItemAccessibleName(int rootIndex) {
        UnifiedElement button = rootButtons(this).get(rootIndex);
        String own = button.getAttribute("aria-label");
        if (own != null && !own.isBlank()) {
            return own;
        }
        for (UnifiedElement item : button.findDescendants("vaadin-menu-bar-item")) {
            String label = item.getAttribute("aria-label");
            if (label != null && !label.isBlank()) {
                return label;
            }
        }
        return button.getText();
    }

    /** The texts of a top-level item's sub-menu, in order; opens and closes it. */
    default List<String> getSubMenuItemTexts(int rootIndex) {
        List<UnifiedElement> items = openSubMenuItems(this, rootIndex);
        List<String> texts = items.stream().map(UnifiedElement::getText).toList();
        items.getFirst().pressKey("Escape");
        return texts;
    }

    /** Opens a top-level item's sub-menu and clicks the item showing {@code text}. */
    default void clickSubMenuItem(int rootIndex, String text) {
        openSubMenuItems(this, rootIndex).stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No sub-menu item '" + text + "' under root item " + rootIndex))
                .click();
    }

    /**
     * Opens the sub-menu of the visible top-level item at {@code rootIndex} (0-based)
     * and returns its items in order, leaving it open.
     */
    default List<Item> openSubMenu(int rootIndex) {
        return openSubMenuItems(this, rootIndex).stream()
                .map(e -> new Item(e.getText(), e.isEnabled()))
                .toList();
    }

    // ---- single-trigger menus (a row's "⋮"): the first top-level item -------

    /** The accessible name of the menu's trigger, its first top-level item. */
    default String getTriggerName() {
        return getRootItemAccessibleName(0);
    }

    /** The texts of the trigger's sub-menu, in order; opens and closes it. */
    default List<String> getSubMenuItems() {
        return getSubMenuItemTexts(0);
    }

    /** Opens the trigger's sub-menu and clicks the item showing {@code text}. */
    default void clickSubMenuItem(String text) {
        clickSubMenuItem(0, text);
    }

    // ---- browser plumbing -------------------------------------------------

    private static List<UnifiedElement> rootButtons(UnifiedMenuBar menu) {
        // The overflow button is a <vaadin-menu-bar-button> too, hidden until needed.
        return menu.element().findChildren("vaadin-menu-bar-button").stream()
                .filter(UnifiedElement::isVisible)
                .toList();
    }

    private static List<UnifiedElement> openSubMenuItems(UnifiedMenuBar menu, int rootIndex) {
        rootButtons(menu).get(rootIndex).click();
        long deadline = System.currentTimeMillis() + SUB_MENU_WAIT_MILLIS;
        while (true) {
            // Only the open sub-menu renders a list box, and it renders it in the
            // menu bar's light DOM (Vaadin 25), so it is inside this scope.
            List<UnifiedElement> items = menu.element().findDescendants("vaadin-menu-bar-list-box")
                    .stream()
                    .filter(UnifiedElement::isVisible)
                    .flatMap(box -> box.findChildren("vaadin-menu-bar-item").stream())
                    .toList();
            if (!items.isEmpty()) {
                return items;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Sub-menu of root item " + rootIndex + " did not open");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /** The Browserless implementation: the server-side items and the tester. */
    interface Browserless extends UnifiedMenuBar {

        private MenuBar menuBar() {
            return element().unwrap(MenuBar.class);
        }

        private List<MenuItem> rootItems() {
            return menuBar().getItems().stream().filter(MenuItem::isVisible).toList();
        }

        @Override
        default int getRootItemCount() {
            return rootItems().size();
        }

        @Override
        default String getRootItemAccessibleName(int rootIndex) {
            MenuItem item = rootItems().get(rootIndex);
            String label = item.getElement().getAttribute("aria-label");
            return label != null && !label.isBlank() ? label : item.getText();
        }

        @Override
        default List<String> getSubMenuItemTexts(int rootIndex) {
            return rootItems().get(rootIndex).getSubMenu().getItems().stream()
                    .filter(MenuItem::isVisible)
                    .map(MenuItem::getText)
                    .toList();
        }

        @Override
        default List<Item> openSubMenu(int rootIndex) {
            // No overlay to open: the items are the server-side sub-menu's.
            return rootItems().get(rootIndex).getSubMenu().getItems().stream()
                    .filter(MenuItem::isVisible)
                    .map(item -> new Item(item.getElement().getTextRecursively(),
                            item.isEnabled()))
                    .toList();
        }

        @Override
        default void clickSubMenuItem(int rootIndex, String text) {
            MenuItem root = rootItems().get(rootIndex);
            List<MenuItem> items = root.getSubMenu().getItems();
            int index = -1;
            for (int i = 0; i < items.size(); i++) {
                if (text.equals(items.get(i).getText())) {
                    index = i;
                }
            }
            if (index < 0) {
                throw new AssertionError(
                        "No sub-menu item '" + text + "' under root item " + rootIndex);
            }
            // Indexes, not captions: an icon-only root item has no caption to match.
            new MenuBarTester<>(menuBar()).clickItem(menuBar().getItems().indexOf(root), index);
        }
    }
}

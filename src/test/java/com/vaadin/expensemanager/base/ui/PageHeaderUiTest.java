package com.vaadin.expensemanager.base.ui;

import java.util.List;

import com.vaadin.expensemanager.allowance.ui.AllowanceRatesView;
import com.vaadin.expensemanager.reference.ui.ExpenseTypeView;
import com.vaadin.expensemanager.reference.ui.VatRateView;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.testing.unified.UnifiedElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedContainer;
import com.vaadin.testing.unified.component.UnifiedParagraph;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified UI test (pyramid layer 3, ADR-0012) for the shared {@link PageHeader}
 * block across the three reference routes it opens — {@code /vat-rates},
 * {@code /expense-types} and {@code /allowance-rates} (#184,
 * {@code docs/design/components/page-header.md}).
 *
 * <p>The three composed the block themselves and had already drifted apart on
 * the gap below the title, so what this asserts is <strong>sameness</strong>:
 * one {@link PageHeader} per route, the same anatomy inside it, the same two
 * gaps around and within it.
 *
 * <p><strong>What is deliberately not here.</strong> That the heading renders at
 * 40px, that the intro's 1.6 leading is unitless rather than Aura's inherited
 * 20px length, that the heading's colour survives dark mode, and that the title
 * row wraps at 360px instead of pushing a scrollbar — none of those are
 * decidable from a component tree on every Testing Tool. They are the visual verification's, and this
 * class stops at the class names and the tokens the two gaps name.
 */
@UnifiedSpringBootTest
@ActiveProfiles("test")
class PageHeaderUiTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }

    /**
     * The create button's label, from the frame. The {@code h2} beside it carries
     * the noun, so the button does not repeat it.
     */
    private static final String CREATE_LABEL = "New";

    @Test
    @WithUserDetails("admin@vaadin.com")
    void vatRatesOpensWithTheSharedHeaderAndACreateButton(UnifiedPage page) {
        page.navigate(VatRateView.class);

        assertHeader(page, "VAT rates", CREATE_LABEL);
        assertSectionRhythm(page);
    }

    @Test
    @WithUserDetails("admin@vaadin.com")
    void expenseTypesOpensWithTheSharedHeaderAndACreateButton(UnifiedPage page) {
        page.navigate(ExpenseTypeView.class);

        assertHeader(page, "Expense types", CREATE_LABEL);
        assertSectionRhythm(page);
    }

    /**
     * The allowance view's own actions seed a <em>year</em> rather than create the
     * page's record, so the design leaves its title row without a button and puts
     * them in the toolbar below. The block is otherwise identical.
     */
    @Test
    @WithUserDetails("admin@vaadin.com")
    void allowanceRatesOpensWithTheSameBlockAndNoHeaderAction(UnifiedPage page) {
        page.navigate(AllowanceRatesView.class);

        assertHeader(page, "Allowance Rates", null);
        assertSectionRhythm(page);
    }

    // ------------------------------------------------------------- assertions

    /**
     * @param title  the page name the {@code h2} must read
     * @param action the create button's label, or {@code null} when the title row
     *               carries no action at all
     */
    private void assertHeader(UnifiedPage page, String title, String action) {
        // One block per page: composed by PageHeader, not by the view.
        assertThat(page.find(UnifiedPageHeader.class)).hasCount(1);
        UnifiedPageHeader header = page.find(UnifiedPageHeader.class);
        // The design's 20px between the title row and the intro, through the
        // layout API rather than CSS.
        assertThat(header.getGap()).isEqualTo("var(--em-card-padding)");

        List<UnifiedElement> parts = header.element().children();
        assertThat(parts).hasSize(2);
        assertThat(classesOf(parts.get(0))).contains("page-header-top");
        assertThat(classesOf(parts.get(1))).contains("page-intro");

        UnifiedContainer top = header.find(UnifiedContainer.class).withClassName("page-header-top");
        // The class carries the display size, not the element — the shell owns
        // the page's h1, so a view's own heading is the second level.
        assertThat(top.findComponent(H2.class).withClassName("page-title")).hasText(title);

        if (action == null) {
            assertThat(top.find(UnifiedButton.class)).doesNotExist();
        } else {
            assertThat(top.find(UnifiedButton.class)).hasCount(1);
            assertThat(top.find(UnifiedButton.class)
                    .withTheme(ButtonVariant.PRIMARY.getVariantName())).hasText(action);
        }

        // The intro is where the consequences live, so it is never absent.
        UnifiedParagraph intro = header.find(UnifiedParagraph.class).withClassName("page-intro");
        assertThat(intro).exists();
        assertThat(intro.getText()).isNotBlank();
    }

    /**
     * The tab bar, the header block and whatever the view puts below it are
     * siblings one section gap apart — the 40px all three frames draw.
     */
    private void assertSectionRhythm(UnifiedPage page) {
        UnifiedVerticalLayout view = page.find(UnifiedVerticalLayout.class).where(e -> {
            List<UnifiedElement> children = e.children();
            return children.size() > 2
                    && classesOf(children.get(0)).contains("reference-tabs")
                    && classesOf(children.get(1)).contains("page-header");
        });
        assertThat(view).exists();
        assertThat(view.getGap()).isEqualTo("var(--em-section-gap)");
    }

    private static List<String> classesOf(UnifiedElement element) {
        String classes = element.getAttribute("class");
        return classes == null ? List.of() : List.of(classes.trim().split("\\s+"));
    }
}

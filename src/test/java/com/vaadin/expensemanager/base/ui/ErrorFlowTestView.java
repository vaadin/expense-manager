package com.vaadin.expensemanager.base.ui;

import com.vaadin.expensemanager.base.DomainRuleException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * Test-only route for {@link ErrorDialogFlowUiTest}: two buttons that fail the two
 * ways issue #86 splits, reachable from a real browser as well as from Browserless.
 * Lives under {@code src/test}, so production never has it.
 *
 * <ul>
 *   <li>{@value #FAIL} — its listener throws a technical exception nothing catches, so
 *       it reaches whatever {@code ErrorHandler} the session has.</li>
 *   <li>{@value #OPEN_EDITOR} — opens an {@link EditorDialog} whose save throws a
 *       {@link DomainRuleException}.</li>
 * </ul>
 */
@Route(ErrorFlowTestView.ROUTE)
@PermitAll
public class ErrorFlowTestView extends VerticalLayout {

    static final String ROUTE = "test-only/error-flow";
    static final String FAIL = "Fail unexpectedly";
    static final String OPEN_EDITOR = "Open editor";
    /** The raw cause; must never reach the user under a non-local profile. */
    static final String CAUSE = "stack trace guts";
    static final String DOMAIN_RULE = "Name is required";

    public ErrorFlowTestView() {
        var fail = new Button(FAIL, event -> {
            throw new IllegalStateException(CAUSE);
        });
        var openEditor = new Button(OPEN_EDITOR, event ->
                new EditorDialog<>("Editor", new TextField("Name"),
                        new Binder<>(Object.class), new Object())
                        .onSave(() -> {
                            throw new DomainRuleException(DOMAIN_RULE);
                        })
                        .open());
        add(fail, openEditor);
    }
}

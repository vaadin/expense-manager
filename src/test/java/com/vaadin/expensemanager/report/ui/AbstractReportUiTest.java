package com.vaadin.expensemanager.report.ui;

import com.vaadin.expensemanager.base.AbstractUiTest;
import com.vaadin.expensemanager.reference.ReferenceDataService;
import com.vaadin.expensemanager.report.service.ExpenseReportRepository;
import com.vaadin.expensemanager.report.service.ExpenseReportService;
import com.vaadin.expensemanager.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Base for the Unified report view tests (ADR-0027, proposed): {@link AbstractUiTest}
 * plus the report services, for seeding state and asserting what a UI action
 * persisted, and the shared {@link ReportFixtures}.
 */
abstract class AbstractReportUiTest extends AbstractUiTest {

    @Autowired
    protected ExpenseReportService service;

    @Autowired
    private ExpenseReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReferenceDataService referenceData;

    @Autowired
    private PlatformTransactionManager transactions;

    protected ReportFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new ReportFixtures(service, reportRepository, userRepository,
                referenceData, transactions);
    }
}

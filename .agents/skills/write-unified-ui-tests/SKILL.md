---
name: write-unified-ui-tests
description: Write, change or fix Unified UI Tests (Vaadin tests that take a UnifiedPage and use Unified* Wrappers), including reading a UnifiedAssertionError when one fails.
---
<!-- unified-test-skill: write-unified-ui-tests 0.1.0-alpha3 -->

# Write Unified UI Tests

A Unified UI Test is a JUnit test written only against the Unified API, so it runs on every Testing Tool:
Browserless (the default), Playwright and Selenium. It reads as the scenario a user goes through, and every run
reports that scenario as User Steps (`fill text field “Name” with “Alan M. Turing”`). Write it so that a reviewer
reading the diff, and anyone reading a failure, sees what the user does. The rules are
[spec chapter 12](https://parttio.github.io/unified-testing-tool/spec/12-readable-tests/); the
[developer guide](https://parttio.github.io/unified-testing-tool/guide/) shows each component.

## Steps

1. **Read the view.** Open the view class the scenario is about, and the components it builds. Note for each
   component the test touches what the user sees: its label, its text, its header title, or its test id. When the
   view calls `getTranslation("key")`, look the key up in `src/main/resources/vaadin-i18n/translations*.properties`.
   Done when every lookup you are about to write names something you saw in the view's code.
2. **Write the test** in the shape below. Read the Wrapper's methods in the `unified-test-api` sources (or its
   Javadoc) before calling one. Done when it compiles: `mvn test-compile`.
3. **Prove it** (see below). Done when it passes on Browserless and Playwright, the style rules pass, and
   `user-steps.txt` tells the scenario you were asked for.
4. **Report** the test's `@DisplayName`, the two runs, and its `user-steps.txt`.

## Shape of a test

```java
@UnifiedSpringBootTest                          // @UnifiedUITest without Spring Boot
class EditPersonTest {

    @Test
    @DisplayName("Renaming a person in the editor updates the grid")
    void renamesAPerson(UnifiedPage page) {
        // given
        page.navigate(PeopleView.class);
        UnifiedGrid people = page.find(UnifiedGrid.class);

        // when
        renamePerson(page, "Alan Turing", "Alan M. Turing");

        // then
        assertThat(page.find(UnifiedNotification.class)).hasText("Saved Alan M. Turing");
        assertThat(people.row().withCellText("Name", "Alan M. Turing")).hasCellText("Email", "alan@example.com");
    }

    private static void renamePerson(UnifiedPage page, String name, String newName) {
        page.step("Rename " + name, () -> {
            page.find(UnifiedGrid.class).row().withCellText("Name", name).click();
            page.find(UnifiedTextField.class).withLabel("Name").setValue(newName);
            page.find(UnifiedButton.class).withText("Save").click();
        });
    }
}
```

- **`@DisplayName`** on every test: the scenario in one sentence, in the user's words. It heads the reports.
- **`// given`, `// when`, `// then`** comments mark the scenario's shape. Leave out the ones with nothing under them.
- **User Task**: a method whose whole body is one `page.step("<user work>", () -> …)`, named after what the user
  does ("Rename Alan Turing", "Fill the billing address"). Extract one when a piece of user work takes several
  User Steps or repeats. Keep it a `private static` method of the test class until a second test class needs it;
  then move it to a `<View>Tasks` class of `static` methods that take the `UnifiedPage`
  (`PeopleTasks.renamePerson(page, ..)`), and call that from both.
- **Wrappers are lazy**: declare one before a step (`UnifiedGrid people = page.find(UnifiedGrid.class);`) and use
  it inside the step and after it.
- **Secrets**: type a password or token through `page.secret(..)`:
  `.setValue(page.secret(apiKey))`. The reports then show `••••`.
- **Page objects** are optional and hold structure only: methods returning Wrappers
  (`UnifiedTextField name() { return page.find(UnifiedTextField.class).withLabel("Name"); }`). User work goes in
  User Tasks, checks in the test.

## Canonical forms

| Intent | Write |
|---|---|
| a component | `page.find(UnifiedTextField.class).withLabel("Name")` — the Wrapper itself, every use finds exactly one |
| by what the user sees | `withLabel(..)` for fields, `withText(..)` for buttons and text, `withHeaderTitle(..)` for dialogs, `withAccessibleName(..)` for icon buttons, `withPlaceholder(..)` for fields without a label |
| by what the user can't see | `withTestId(..)`, `withId(..)`: only when no visible name tells it apart |
| inside a container or dialog | `dialog.find(UnifiedTextField.class).withLabel("Email")`, `page.find(UnifiedContainer.class).withTestId("billing").find(..)` |
| a component without a Wrapper | `page.findComponent(MyComponent.class)` |
| a key press | `page.pressKey("Enter")`, sent to the focused component |
| a grid row | `grid.row().withCellText("Name", "Alan Turing")`; `grid.row(0)` by index; cell components: `row.cell("Actions").find(UnifiedButton.class)` |
| a check | `assertThat(wrapper).hasText(..)`, `.hasValue(..)`, `.isOpen()`, `.isDisabled()`, `assertThat(row).hasCellText(..)`, `assertThat(page).isAt(View.class)` |
| present, absent, how many | `assertThat(wrapper).exists()`, `.doesNotExist()`, `.hasCount(n)`, also on grid rows |
| a condition no assertion states | `page.waitUntil(() -> ..)`: a last resort, it shows as a gap in the reports |
| every match, to act on each | `for (UnifiedButton b : page.find(UnifiedButton.class).withText("Done").all()) { .. }` |

Wrappers are the `Unified*` interfaces in `com.vaadin.testing.unified.component`; `assertThat` is
`import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;`. Its assertions
retry until the timeout, so a check waits for the server's answer by itself. Check what the user sees with these
assertions; a plain JUnit `assertEquals` on a Wrapper's getter reads once, without waiting, and shows in the reports
as a gap. Use `all()` only to loop. When a lookup matches several components, add the filter that names the one
meant (often a container) rather than picking one by position.

## Prove it

Run the test class on both Testing Tools (add `-pl <module>` in a multi-module build):

```
mvn test -Dtest=EditPersonTest
mvn test -Dtest=EditPersonTest -Dvaadin.testing.tool=playwright
```

Playwright needs Chromium once: `mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI
-Dexec.classpathScope=test -Dexec.args="install chromium"`.

Then run the style rules: `mvn test -Dtest=UnifiedStyleTest`. When the project has no such test, add one: the test
dependency `org.vaadin.addons:unified-test-archunit` (its version comes from `unified-test-bom`) and

```java
class UnifiedStyleTest {
    @Test
    @DisplayName("Every Unified UI Test follows the style rules")
    void followsTheUnifiedStyle() {
        UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.example"));
    }
}
```

Last, read `target/surefire-reports/unified-evidence/<TestClass>/<testMethod>/user-steps.txt`. Each line is one
User Step. Read it as the person who asked for the scenario would: every action they described appears, in their
order, on the components they named, and every outcome they expect is a `see` line. A step you meant but don't see
is a test that does less than asked: fix the test.

## Read a failure

Every Unified failure is a `UnifiedAssertionError`, with these lines (lines with nothing to say are left out):

```
UnifiedAssertionError: Renaming a person in the editor updates the grid
  Failed at     User Step 3 of 5: fill text field “E-mail” with “alan@bletchley.uk”
  In step       Rename Alan Turing
  Expected      exactly one text field labelled “E-mail”
  Found         none
  Nearby        text field “Email”, text field “Name”
  Did you mean  find(UnifiedTextField.class).withLabel("Email")
  Waited        10 s (vaadin.testing.timeout)
  Tool          playwright (chromium)
  Code          EditPersonTest.java:14 (renamesAPerson) ← EditPersonTest.java:27 (renamePerson)
  Evidence      target/surefire-reports/unified-evidence/EditPersonTest/renamesAPerson/
```

`Nearby` lists the Near Misses, closest first; `Found` is `none`, `2: …` (several matches: add a filter),
`1, but disabled` (the user can't use it yet: do the step that enables it), or the values an assertion saw while
waiting. `Evidence` holds a screenshot (browser) or the component tree (Browserless).

Decide who is wrong before changing anything:
- **The test is wrong** when the view's code shows what the Near Miss shows (the label is "Email", the test guessed
  "E-mail"): apply `Did you mean`, or write the lookup from the view.
- **The application is wrong** when the scenario you were given says the user should see something else (the
  notification, a saved value, a row): stop and report the failure's `Expected` and `Found` to the person who asked,
  with the view code involved. Change the expectation only when they confirm the new behaviour is intended.

## The Escape Hatch

`unwrap(..)` leaves the Unified API for one Testing Tool's own object (Playwright `Page`, Browserless
`BrowserlessUIContext`, a Flow component). Use it only for what the Unified API cannot say (network mocking,
arbitrary JavaScript, server-side state). The test then declares the Testing Tool, and a one-line comment says why:

```java
@Test
@DisplayName("Offline users see the retry banner")
@RequiresTestingTool(TestingTool.PLAYWRIGHT)
void showsTheRetryBanner(UnifiedPage page) {
    // Escape Hatch: the Unified API can't take the browser offline
    page.unwrap(Page.class).context().setOffline(true);
    ...
}
```

For an extra check that only some Testing Tools can make, use `tryUnwrap(..)`; the test keeps running everywhere:
`name.tryUnwrap(TextField.class).ifPresent(field -> assertEquals("Name", field.getLabel()));`.

## Provenance

- **Upstream:** Unified Test (`org.vaadin.addons:unified-test-rewrite`), [https://github.com/parttio/unified-testing-tool](https://github.com/parttio/unified-testing-tool)
- **Source path:** `META-INF/unified/skills/write-unified-ui-tests/SKILL.md` inside `unified-test-rewrite-0.1.0-alpha3.jar`
- **Version:** 0.1.0-alpha3 (jar sha256 `a983271722634661d8c5d68acd566ec645e67eefe4036549cdda92aaecffafb3`; upstream SKILL.md sha256 `2274649b54e368e2146fa99e6606df0b07d1d23910cd8808eca1c7072a21f7f1`)
- **Copied:** 2026-11-18, by hand, as a project-owned file. **Not** managed by `skills.sh` / `skills-lock.json`.
- **Locally modified:** no. Only this Provenance section was added, so the migration log
  (`docs/unified-testing-alpha3-migration.md`) measures the skill as shipped.

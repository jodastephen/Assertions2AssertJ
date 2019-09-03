# Assertions2AssertJ for TestNG

This is a fork of the original [Assertions2AssertJ project](https://github.com/ricemery/Assertions2AssertJ), thanks!

The project provides an IntelliJ plugin that does two things:

* convert code from TestNG to JUnit 5
* convert assertions from TestNG to AssertJ

It can be run on a single file, module or an entire project.
It will try and do the following:

* Convert `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`, `assertSame`, `assertNotSame`, `assertThrows` and a few others to suitable AssertJ method calls.
* Change `@Test` from class level to method level
* Convert `@Test(enabled=false)` to `@Disabled` together with the description for disabling
* Convert `@DataProvider` and `@Test(dataProvider=)` to '@ParameterizedTest' and `@MethodSource` using a naming convention approach to the provider name
* Convert `@BeforeTest`, `@BeforeClass` and `@BeforeMethod` and the equivalent "after" annotations to `@BeforeAll`, `@BeforeEach` etc.

The key point is that conversion is based on an AST representation of the code.
This is not a regex-based converter, making it much more powerful. 

The conversion is not perfect however.
The `@Test(expectedExceptions)` clause in particular is not handled,
and some AssertJ conversions, such as `assertThat().hasSize()`,
are a little bit too aggressive.
Some manual intervention is usually needed.

Having said all of the above, the plugin has been used with success
and minimal manual intervention on very large projects at
[OpenGamma](https://opengamma.com/), such as [Strata](https://github.com/OpenGamma/Strata/commit/1dd64e965041a1e3fb81adf8ce9156c451d8252b). 

### Usage

This TestNG conversion tool is **not available** via the standard IntelliJ plugin search.
To use it, you must do the following:

* Clone this repo
* Build the repo using `gradle build`
* Install the zip file into IntelliJ
  * Go to File/Settings then Plugins
  * Click the settings gear icon in the top right
  * Choose "Install plugin from disk"
  * Select the zip file under `build/distributions`
  * Restart the IDE

Select an item from within the Refactor -\> Convert Assertions to AssertJ menu.
Note that the "Convert current file" and "Convert Module" items will
only be enabled if a file is selected within the editor.

Note that TestNG, JUnit 5 and AssertJ must all be included in the IntelliJ
project classpath for the Plugin to successfully complete.

### Why convert from TestNG?

In the beginning TestNG was clearly superior to JUnit.
JUnit 5 has changed the situation, and it is now the testing framework to be on.

That said, I personally find JUnit `assertEquals` methods to be "backwards".
As such, it made sense to also adopt `AssertJ`, the leading assertion framework.

### Support

I don't intend to maintain this repo, accept PRs or respond to issues.
You may need to make changes or enhancements depending on your use case.
The code isn't that complex, however as a fair warning,
the IntelliJ PSI library is nastily mutable and sometimes seems very flaky.
There is a rudimentary logging mechanism in the code, see `Util.kt`, which 
may or may not help you.

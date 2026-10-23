// Drives `.interactive()` presses for #1409, because simctl can't inject touches. Run while the screen
// is recorded: each test waits 2 s, holds a 3 s press on pill A, then idles 2 s for the release.

import XCTest

final class PressTests: XCTestCase {
  func testPressJoined() { press(joined: "YES") }
  func testPressApart() { press(joined: "NO") }
  func testPressUnion() { press(joined: "NO", scenario: "unionInteractive") }

  private func press(joined: String, scenario: String = "interactive") {
    let app = XCUIApplication()
    app.launchArguments = ["-scenario", scenario, "-joined", joined]
    app.launch()
    sleep(2)
    app.staticTexts["A"].press(forDuration: 3)
    sleep(2)
  }
}

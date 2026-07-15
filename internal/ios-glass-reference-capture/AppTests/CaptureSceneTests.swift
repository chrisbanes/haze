import CoreGraphics
import Testing
@testable import CaptureApp

@Test
func everySceneParsesFromCaptureArgument() throws {
    for scene in CaptureScene.allCases {
        let parsed = try CaptureScene.parse(
            arguments: ["app", "--capture-scene", scene.rawValue]
        )

        #expect(parsed == scene)
    }
}

@Test
func invalidCaptureArgumentsThrow() {
    #expect(throws: CaptureArgumentError.missingScene) {
        try CaptureScene.parse(arguments: ["app"])
    }
    #expect(throws: CaptureArgumentError.unknownScene("other")) {
        try CaptureScene.parse(arguments: ["app", "--capture-scene", "other"])
    }
}

@Test
func missingCaptureSceneValueThrowsMissingScene() {
    #expect(throws: CaptureArgumentError.missingScene) {
        try CaptureScene.parse(arguments: ["app", "--capture-scene"])
    }
}

@Test
func duplicateCaptureSceneFlagsThrowInvalidArguments() {
    #expect(throws: CaptureArgumentError.invalidArguments) {
        try CaptureScene.parse(arguments: [
            "app", "--capture-scene", "uniform-light", "--capture-scene", "grid-dark",
        ])
    }
}

@Test
func leadingExtraOptionThrowsInvalidArguments() {
    #expect(throws: CaptureArgumentError.invalidArguments) {
        try CaptureScene.parse(arguments: [
            "app", "--verbose", "--capture-scene", "uniform-light",
        ])
    }
}

@Test
func trailingArgumentThrowsInvalidArguments() {
    #expect(throws: CaptureArgumentError.invalidArguments) {
        try CaptureScene.parse(arguments: [
            "app", "--capture-scene", "uniform-light", "trailing",
        ])
    }
}

@Test
func outputFilenamesMatchSceneOrder() {
    #expect(
        CaptureScene.allCases.map(\.outputFilename) == [
            "uniform-light.png",
            "uniform-dark.png",
            "grid-light.png",
            "grid-dark.png",
        ]
    )
}

@Test
func referenceLayoutUsesFixedViewportAndSurfaceFrames() {
    #expect(ReferenceLayout.viewportSize == CGSize(width: 360, height: 720))
    #expect(
        ReferenceLayout.surfaceFrames == [
            .capsule: CGRect(x: 60, y: 90, width: 240, height: 64),
            .card: CGRect(x: 40, y: 224, width: 280, height: 176),
            .panel: CGRect(x: 20, y: 460, width: 320, height: 220),
        ]
    )
}

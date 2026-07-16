import CoreGraphics
import Foundation
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
func malformedCaptureSceneValuesAreRejected() {
    for value in ["uniform--light", "size-small-grid-dark-"] {
        #expect(CaptureScene(rawValue: value) == nil)
        #expect(throws: CaptureArgumentError.unknownScene(value)) {
            try CaptureScene.parse(arguments: ["app", "--capture-scene", value])
        }
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
        CaptureScene.allCases.map(\.rawValue) == [
            "uniform-light", "uniform-dark", "grid-light", "grid-dark",
            "size-small-uniform-light", "size-small-uniform-dark", "size-small-grid-light", "size-small-grid-dark",
            "size-medium-uniform-light", "size-medium-uniform-dark", "size-medium-grid-light", "size-medium-grid-dark",
            "size-large-uniform-light", "size-large-uniform-dark", "size-large-grid-light", "size-large-grid-dark",
            "aspect-uniform-light", "aspect-uniform-dark", "aspect-grid-light", "aspect-grid-dark",
            "roundness-uniform-light", "roundness-uniform-dark", "roundness-grid-light", "roundness-grid-dark",
        ]
    )
    #expect(CaptureScene.allCases.prefix(4).map(\.outputFilename) == [
        "uniform-light.png", "uniform-dark.png", "grid-light.png", "grid-dark.png",
    ])
}

@Test
func scenesUsePageMajorRawValuesAndRoundTrip() throws {
    #expect(CaptureScene.allCases.count == 24)
    #expect(CaptureScene(page: .sizeSmall, appearance: .dark, background: .grid).rawValue == "size-small-grid-dark")

    for scene in CaptureScene.allCases {
        #expect(CaptureScene(rawValue: scene.rawValue) == scene)
        #expect(try JSONDecoder().decode(CaptureScene.self, from: JSONEncoder().encode(scene)) == scene)
    }
}

@Test
func captureSceneCodableUsesRawJSONValue() throws {
    let scene = CaptureScene(page: .sizeSmall, appearance: .dark, background: .grid)
    let encoded = try JSONEncoder().encode(scene)

    #expect(String(data: encoded, encoding: .utf8) == "\"size-small-grid-dark\"")
    #expect(try JSONDecoder().decode(CaptureScene.self, from: encoded) == scene)
    #expect(throws: DecodingError.self) {
        try JSONDecoder().decode(CaptureScene.self, from: Data("\"other\"".utf8))
    }
}

@Test
func referenceLayoutPreservesBaselineGeometry() {
    #expect(ReferenceLayout.viewportSize == CGSize(width: 360, height: 720))
    #expect(ReferenceLayout.surfaces(for: .baseline) == [
        ReferenceSurface(id: "capsule", frame: CGRect(x: 60, y: 90, width: 240, height: 64), shape: .capsule, role: .regression),
        ReferenceSurface(id: "card", frame: CGRect(x: 40, y: 224, width: 280, height: 176), shape: .roundedRectangle(cornerRadius: 28), role: .regression),
        ReferenceSurface(id: "panel", frame: CGRect(x: 20, y: 460, width: 320, height: 220), shape: .roundedRectangle(cornerRadius: 24), role: .regression),
    ])
    #expect(ReferenceLayout.surfaceFrames == [
        .capsule: CGRect(x: 60, y: 90, width: 240, height: 64),
        .card: CGRect(x: 40, y: 224, width: 280, height: 176),
        .panel: CGRect(x: 20, y: 460, width: 320, height: 220),
    ])
}

@Test
func calibrationSurfacesHaveApprovedGeometryRolesAndStayInViewport() {
    #expect(ReferenceLayout.surfaces(for: .sizeSmall).map(\.id) == ["size-44", "size-64", "size-88"])
    #expect(ReferenceLayout.surfaces(for: .sizeMedium).map(\.id) == ["size-112", "size-144", "size-176"])
    #expect(ReferenceLayout.surfaces(for: .sizeLarge).map(\.id) == ["size-220"])
    #expect(ReferenceLayout.surfaces(for: .aspect).map(\.id) == ["aspect-1", "aspect-1_5", "aspect-2", "aspect-3", "aspect-4"])
    #expect(ReferenceLayout.surfaces(for: .roundness).map(\.id) == ["roundness-0", "roundness-12", "roundness-24", "roundness-36", "roundness-48"])

    let sizeSurfaces = ReferenceLayout.surfaces(for: .sizeSmall) + ReferenceLayout.surfaces(for: .sizeMedium) + ReferenceLayout.surfaces(for: .sizeLarge)
    #expect(sizeSurfaces.map(\.logicalSize) == [CGSize(width: 66, height: 44), CGSize(width: 96, height: 64), CGSize(width: 132, height: 88), CGSize(width: 168, height: 112), CGSize(width: 216, height: 144), CGSize(width: 264, height: 176), CGSize(width: 330, height: 220)])
    #expect(sizeSurfaces.map { $0.frame.minY } == [40, 144, 288, 24, 184, 384, 250])
    #expect(sizeSurfaces.map(\.cornerRadius) == [11, 16, 22, 28, 36, 44, 55])
    #expect(sizeSurfaces.map(\.role) == [.training, .holdout, .training, .holdout, .training, .holdout, .training])

    let aspectSurfaces = ReferenceLayout.surfaces(for: .aspect)
    #expect(aspectSurfaces.map(\.logicalSize) == [CGSize(width: 80, height: 80), CGSize(width: 120, height: 80), CGSize(width: 160, height: 80), CGSize(width: 240, height: 80), CGSize(width: 320, height: 80)])
    #expect(aspectSurfaces.map { $0.frame.minY } == [24, 152, 280, 408, 536])
    #expect(aspectSurfaces.allSatisfy { $0.cornerRadius == 20 })
    #expect(aspectSurfaces.map(\.role) == [.training, .holdout, .training, .holdout, .training])

    let roundnessSurfaces = ReferenceLayout.surfaces(for: .roundness)
    #expect(roundnessSurfaces.map(\.frame) == [
        CGRect(x: 60, y: 24, width: 240, height: 96),
        CGRect(x: 60, y: 152, width: 240, height: 96),
        CGRect(x: 60, y: 280, width: 240, height: 96),
        CGRect(x: 60, y: 408, width: 240, height: 96),
        CGRect(x: 60, y: 536, width: 240, height: 96),
    ])
    #expect(roundnessSurfaces.map(\.cornerRadius) == [0, 12, 24, 36, 48])
    #expect(roundnessSurfaces.map { $0.frame.minY } == [24, 152, 280, 408, 536])
    #expect(roundnessSurfaces.map(\.role) == [.training, .holdout, .training, .holdout, .training])

    for page in ReferencePage.allCases {
        for surface in ReferenceLayout.surfaces(for: page) {
            #expect(surface.frame.minX >= 0)
            #expect(surface.frame.minY >= 0)
            #expect(surface.frame.maxX <= ReferenceLayout.viewportSize.width)
            #expect(surface.frame.maxY <= ReferenceLayout.viewportSize.height)
            #expect(surface.frame.midX == ReferenceLayout.viewportSize.width / 2)
        }
    }
}

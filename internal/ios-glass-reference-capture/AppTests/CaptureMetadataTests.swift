import CoreGraphics
import Foundation
import Testing
import UIKit
@testable import CaptureApp

@Test
func readinessContainsOnlySelectedPageSurfaces() {
    let scene = CaptureScene(page: .sizeLarge, appearance: .dark, background: .grid)
    let ready = CaptureReady.make(
        scene: scene,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )

    #expect(ready.schemaVersion == 2)
    #expect(ready.page == .sizeLarge)
    #expect(ready.viewport == PixelRect(x: 63, y: 231, width: 1080, height: 2160))
    #expect(ready.surfaces == [
        "size-220": CaptureSurface(
            frame: PixelRect(x: 45, y: 750, width: 990, height: 660),
            cornerRadius: 165,
            role: .training,
        ),
    ])
}

@Test
func baselineReadinessPreservesPixelGeometryAndRoles() {
    let ready = CaptureReady.make(
        scene: .gridDark,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )

    #expect(ready.page == .baseline)
    #expect(ready.surfaces == [
        "capsule": CaptureSurface(
            frame: PixelRect(x: 180, y: 270, width: 720, height: 192),
            cornerRadius: 96,
            role: .regression,
        ),
        "card": CaptureSurface(
            frame: PixelRect(x: 120, y: 672, width: 840, height: 528),
            cornerRadius: 84,
            role: .regression,
        ),
        "panel": CaptureSurface(
            frame: PixelRect(x: 60, y: 1380, width: 960, height: 660),
            cornerRadius: 72,
            role: .regression,
        ),
    ])
}

@Test
func serializesStableReadyPayload() throws {
    let ready = CaptureReady.make(
        scene: .uniformLight,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )

    let decoded = try JSONDecoder().decode(CaptureReady.self, from: try ready.encoded())

    #expect(decoded == ready)
}

@Test
func encodesStableReadyPayloadContract() throws {
    let ready = CaptureReady.make(
        scene: .uniformLight,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )

    let encoded = try #require(String(data: ready.encoded(), encoding: .utf8))

    #expect(encoded == """
    {
      "colorSpace" : "sRGB",
      "framebuffer" : {
        "height" : 2622,
        "width" : 1206,
        "x" : 0,
        "y" : 0
      },
      "page" : "baseline",
      "safeAreaInsets" : {
        "bottom" : 102,
        "leading" : 0,
        "top" : 186,
        "trailing" : 0
      },
      "scale" : 3,
      "scene" : "uniform-light",
      "schemaVersion" : 2,
      "surfaces" : {
        "capsule" : {
          "cornerRadius" : 96,
          "frame" : {
            "height" : 192,
            "width" : 720,
            "x" : 180,
            "y" : 270
          },
          "role" : "regression"
        },
        "card" : {
          "cornerRadius" : 84,
          "frame" : {
            "height" : 528,
            "width" : 840,
            "x" : 120,
            "y" : 672
          },
          "role" : "regression"
        },
        "panel" : {
          "cornerRadius" : 72,
          "frame" : {
            "height" : 660,
            "width" : 960,
            "x" : 60,
            "y" : 1380
          },
          "role" : "regression"
        }
      },
      "viewport" : {
        "height" : 2160,
        "width" : 1080,
        "x" : 63,
        "y" : 231
      }
    }
    """)
}

@Test
func writeAtomicallyCreatesAndReplacesReadyPayload() throws {
    let directory = try makeTemporaryDirectory()
    defer { try? FileManager.default.removeItem(at: directory) }

    let url = directory.appendingPathComponent("capture-ready.json")
    let initial = CaptureReady.make(
        scene: .uniformLight,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )
    try initial.writeAtomically(to: url)

    let storedInitial = try JSONDecoder().decode(CaptureReady.self, from: Data(contentsOf: url))
    #expect(storedInitial == initial)
    #expect(!FileManager.default.fileExists(atPath: url.appendingPathExtension("tmp").path))

    let replacement = CaptureReady.make(
        scene: .gridDark,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )
    try replacement.writeAtomically(to: url)

    let storedReplacement = try JSONDecoder().decode(CaptureReady.self, from: Data(contentsOf: url))
    #expect(storedReplacement == replacement)
    #expect(!FileManager.default.fileExists(atPath: url.appendingPathExtension("tmp").path))
}

@Test
func readinessPolicyRequiresDisplayFramesAndForegroundSettling() {
    #expect(CaptureReadinessPolicy.minimumDisplayFrames == 2)
    #expect(CaptureReadinessPolicy.minimumForegroundDuration == .milliseconds(1_200))
}

private func makeTemporaryDirectory() throws -> URL {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: false)
    return directory
}

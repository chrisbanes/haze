import CoreGraphics
import Foundation
import Testing
import UIKit
@testable import CaptureApp

@Test
func convertsViewportAndSurfacesToPixels() {
    let ready = CaptureReady.make(
        scene: .gridDark,
        scale: 3,
        framebufferSize: CGSize(width: 402, height: 874),
        safeAreaInsets: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
    )

    #expect(ready.viewport == PixelRect(x: 63, y: 231, width: 1080, height: 2160))
    #expect(ready.surfaces[ReferenceSurface.capsule.rawValue] == PixelRect(x: 180, y: 270, width: 720, height: 192))
    #expect(ready.surfaces[ReferenceSurface.card.rawValue] == PixelRect(x: 120, y: 672, width: 840, height: 528))
    #expect(ready.surfaces[ReferenceSurface.panel.rawValue] == PixelRect(x: 60, y: 1380, width: 960, height: 660))
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
      "safeAreaInsets" : {
        "bottom" : 102,
        "leading" : 0,
        "top" : 186,
        "trailing" : 0
      },
      "scale" : 3,
      "scene" : "uniform-light",
      "schemaVersion" : 1,
      "surfaces" : {
        "capsule" : {
          "height" : 192,
          "width" : 720,
          "x" : 180,
          "y" : 270
        },
        "card" : {
          "height" : 528,
          "width" : 840,
          "x" : 120,
          "y" : 672
        },
        "panel" : {
          "height" : 660,
          "width" : 960,
          "x" : 60,
          "y" : 1380
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

import Foundation
import SwiftUI
import UIKit

struct PixelRect: Codable, Equatable, Sendable {
    let x: Int
    let y: Int
    let width: Int
    let height: Int
}

struct PixelInsets: Codable, Equatable, Sendable {
    let top: Int
    let leading: Int
    let bottom: Int
    let trailing: Int
}

struct CaptureReady: Codable, Equatable, Sendable {
    let schemaVersion: Int
    let scene: CaptureScene
    let scale: Double
    let colorSpace: String
    let framebuffer: PixelRect
    let safeAreaInsets: PixelInsets
    let viewport: PixelRect
    let surfaces: [String: PixelRect]

    static func make(
        scene: CaptureScene,
        scale: CGFloat,
        framebufferSize: CGSize,
        safeAreaInsets: UIEdgeInsets,
    ) -> CaptureReady {
        precondition(scale > 0)

        func pixels(_ value: CGFloat) -> Int {
            Int((value * scale).rounded())
        }

        func pixelRect(_ rect: CGRect) -> PixelRect {
            PixelRect(
                x: pixels(rect.minX),
                y: pixels(rect.minY),
                width: pixels(rect.width),
                height: pixels(rect.height),
            )
        }

        let viewportOrigin = CGPoint(
            x: (framebufferSize.width - ReferenceLayout.viewportSize.width) / 2,
            y: (framebufferSize.height - ReferenceLayout.viewportSize.height) / 2,
        )

        return CaptureReady(
            schemaVersion: 1,
            scene: scene,
            scale: Double(scale),
            colorSpace: "sRGB",
            framebuffer: PixelRect(
                x: 0,
                y: 0,
                width: pixels(framebufferSize.width),
                height: pixels(framebufferSize.height),
            ),
            safeAreaInsets: PixelInsets(
                top: pixels(safeAreaInsets.top),
                leading: pixels(safeAreaInsets.left),
                bottom: pixels(safeAreaInsets.bottom),
                trailing: pixels(safeAreaInsets.right),
            ),
            viewport: PixelRect(
                x: pixels(viewportOrigin.x),
                y: pixels(viewportOrigin.y),
                width: pixels(ReferenceLayout.viewportSize.width),
                height: pixels(ReferenceLayout.viewportSize.height),
            ),
            surfaces: Dictionary(uniqueKeysWithValues: ReferenceSurface.allCases.map { surface in
                (surface.rawValue, pixelRect(ReferenceLayout.surfaceFrames[surface]!))
            }),
        )
    }

    func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(self)
    }

    static var readinessURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("capture-ready.json")
    }

    func writeAtomically(to url: URL = CaptureReady.readinessURL) throws {
        let fileManager = FileManager.default
        let temporaryURL = url.appendingPathExtension("tmp")

        try? fileManager.removeItem(at: temporaryURL)
        try encoded().write(to: temporaryURL, options: .atomic)

        if fileManager.fileExists(atPath: url.path) {
            _ = try fileManager.replaceItemAt(url, withItemAt: temporaryURL)
        } else {
            try fileManager.moveItem(at: temporaryURL, to: url)
        }
    }
}

@MainActor
final class DisplayFrameWaiter: NSObject {
    private var remaining = 0
    private var continuation: CheckedContinuation<Void, Never>?
    private var displayLink: CADisplayLink?

    func wait(frames: Int) async {
        precondition(frames > 0)
        precondition(displayLink == nil)

        remaining = frames
        await withCheckedContinuation { continuation in
            self.continuation = continuation

            let displayLink = CADisplayLink(target: self, selector: #selector(tick))
            self.displayLink = displayLink
            displayLink.add(to: .main, forMode: .common)
        }
    }

    @objc private func tick() {
        remaining -= 1

        guard remaining == 0 else {
            return
        }

        displayLink?.invalidate()
        displayLink = nil

        let continuation = continuation
        self.continuation = nil
        continuation?.resume()
    }
}

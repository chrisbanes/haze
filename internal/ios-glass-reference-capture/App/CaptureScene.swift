import CoreGraphics
import SwiftUI

enum CaptureArgumentError: Error, Equatable {
    case missingScene
    case unknownScene(String)
    case invalidArguments
}

enum CaptureScene: String, CaseIterable, Codable, Sendable {
    case uniformLight = "uniform-light"
    case uniformDark = "uniform-dark"
    case gridLight = "grid-light"
    case gridDark = "grid-dark"

    static func parse(arguments: [String]) throws -> CaptureScene {
        guard arguments.count >= 3 else {
            throw CaptureArgumentError.missingScene
        }

        guard arguments.count == 3 else {
            throw CaptureArgumentError.invalidArguments
        }

        guard arguments[1] == "--capture-scene" else {
            throw CaptureArgumentError.missingScene
        }

        let value = arguments[2]
        guard let scene = CaptureScene(rawValue: value) else {
            throw CaptureArgumentError.unknownScene(value)
        }
        return scene
    }

    var outputFilename: String {
        "\(rawValue).png"
    }

    var colorScheme: ColorScheme {
        switch self {
        case .uniformLight, .gridLight:
            .light
        case .uniformDark, .gridDark:
            .dark
        }
    }

    var isGrid: Bool {
        switch self {
        case .gridLight, .gridDark:
            true
        case .uniformLight, .uniformDark:
            false
        }
    }

    var background: Color {
        switch colorScheme {
        case .light:
            Color(red: 242.0 / 255.0, green: 245.0 / 255.0, blue: 248.0 / 255.0)
        case .dark:
            Color(red: 16.0 / 255.0, green: 24.0 / 255.0, blue: 32.0 / 255.0)
        @unknown default:
            Color.black
        }
    }
}

enum ReferenceSurface: String, CaseIterable, Codable, Sendable {
    case capsule
    case card
    case panel
}

enum ReferenceLayout {
    static let viewportSize = CGSize(width: 360, height: 720)

    static let surfaceFrames: [ReferenceSurface: CGRect] = [
        .capsule: CGRect(x: 60, y: 90, width: 240, height: 64),
        .card: CGRect(x: 40, y: 224, width: 280, height: 176),
        .panel: CGRect(x: 20, y: 460, width: 320, height: 220),
    ]
}

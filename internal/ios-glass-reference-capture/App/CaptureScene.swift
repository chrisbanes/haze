import CoreGraphics
import SwiftUI

enum CaptureArgumentError: Error, Equatable {
    case missingScene
    case unknownScene(String)
    case invalidArguments
}

enum ReferencePage: String, CaseIterable, Codable, Sendable {
    case baseline
    case sizeSmall = "size-small"
    case sizeMedium = "size-medium"
    case sizeLarge = "size-large"
    case aspect
    case roundness
}

enum GlassAppearance: String, CaseIterable, Codable, Sendable {
    case light
    case dark
}

enum CaptureBackground: String, CaseIterable, Codable, Sendable {
    case uniform
    case grid
}

enum CalibrationRole: String, Codable, Sendable {
    case training
    case holdout
    case regression
}

enum ReferenceShape: Codable, Equatable, Hashable, Sendable {
    case capsule
    case roundedRectangle(cornerRadius: CGFloat)
}

struct ReferenceSurface: Identifiable, Hashable, Codable, Sendable {
    let id: String
    let frame: CGRect
    let shape: ReferenceShape
    let role: CalibrationRole

    var rawValue: String { id }
    var logicalSize: CGSize { frame.size }
    var cornerRadius: CGFloat {
        switch shape {
        case .capsule:
            min(frame.width, frame.height) / 2
        case let .roundedRectangle(cornerRadius):
            cornerRadius
        }
    }

    static let capsule = ReferenceLayout.baselineSurfaces[0]
    static let card = ReferenceLayout.baselineSurfaces[1]
    static let panel = ReferenceLayout.baselineSurfaces[2]
    static let allCases = [capsule, card, panel]
}

struct CaptureScene: RawRepresentable, CaseIterable, Codable, Equatable, Sendable {
    let page: ReferencePage
    let appearance: GlassAppearance
    let background: CaptureBackground

    init(page: ReferencePage, appearance: GlassAppearance, background: CaptureBackground) {
        self.page = page
        self.appearance = appearance
        self.background = background
    }

    init?(rawValue: String) {
        for page in ReferencePage.allCases {
            let prefix = page == .baseline ? "" : "\(page.rawValue)-"
            guard rawValue.hasPrefix(prefix) else {
                continue
            }

            let components = rawValue.dropFirst(prefix.count).split(separator: "-")
            guard components.count == 2,
                  let background = CaptureBackground(rawValue: String(components[0])),
                  let appearance = GlassAppearance(rawValue: String(components[1]))
            else {
                continue
            }

            let scene = CaptureScene(page: page, appearance: appearance, background: background)
            guard scene.rawValue == rawValue else {
                continue
            }
            self = scene
            return
        }
        return nil
    }

    var rawValue: String {
        let value = "\(background.rawValue)-\(appearance.rawValue)"
        return page == .baseline ? value : "\(page.rawValue)-\(value)"
    }

    static let allCases = ReferencePage.allCases.flatMap { page in
        CaptureBackground.allCases.flatMap { background in
            GlassAppearance.allCases.map { appearance in
                CaptureScene(page: page, appearance: appearance, background: background)
            }
        }
    }

    static let uniformLight = CaptureScene(page: .baseline, appearance: .light, background: .uniform)
    static let uniformDark = CaptureScene(page: .baseline, appearance: .dark, background: .uniform)
    static let gridLight = CaptureScene(page: .baseline, appearance: .light, background: .grid)
    static let gridDark = CaptureScene(page: .baseline, appearance: .dark, background: .grid)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let rawValue = try container.decode(String.self)
        guard let scene = CaptureScene(rawValue: rawValue) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unknown capture scene: \(rawValue)",
            )
        }
        self = scene
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

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
        guard let scene = CaptureScene(rawValue: arguments[2]) else {
            throw CaptureArgumentError.unknownScene(arguments[2])
        }
        return scene
    }

    var outputFilename: String { "\(rawValue).png" }
    var colorScheme: ColorScheme { appearance == .light ? .light : .dark }
    var isGrid: Bool { background == .grid }

    var backgroundColor: Color {
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

enum ReferenceLayout {
    static let viewportSize = CGSize(width: 360, height: 720)

    static let baselineSurfaces = [
        ReferenceSurface(id: "capsule", frame: CGRect(x: 60, y: 90, width: 240, height: 64), shape: .capsule, role: .regression),
        ReferenceSurface(id: "card", frame: CGRect(x: 40, y: 224, width: 280, height: 176), shape: .roundedRectangle(cornerRadius: 28), role: .regression),
        ReferenceSurface(id: "panel", frame: CGRect(x: 20, y: 460, width: 320, height: 220), shape: .roundedRectangle(cornerRadius: 24), role: .regression),
    ]

    static let surfaceFrames = Dictionary(uniqueKeysWithValues: baselineSurfaces.map { ($0, $0.frame) })

    static func surfaces(for page: ReferencePage) -> [ReferenceSurface] {
        switch page {
        case .baseline:
            baselineSurfaces
        case .sizeSmall:
            sizeSurfaces(Array(sizeMeasurements.prefix(3)))
        case .sizeMedium:
            sizeSurfaces(Array(sizeMeasurements.dropFirst(3).prefix(3)))
        case .sizeLarge:
            sizeSurfaces(Array(sizeMeasurements.suffix(1)))
        case .aspect:
            aspectSurfaces
        case .roundness:
            roundnessSurfaces
        }
    }

    private static let sizeMeasurements: [(id: String, height: CGFloat, y: CGFloat, role: CalibrationRole)] = [
        ("size-44", 44, 40, .training), ("size-64", 64, 144, .holdout), ("size-88", 88, 288, .training),
        ("size-112", 112, 24, .holdout), ("size-144", 144, 184, .training), ("size-176", 176, 384, .holdout),
        ("size-220", 220, 250, .training),
    ]

    private static func sizeSurfaces(_ measurements: [(id: String, height: CGFloat, y: CGFloat, role: CalibrationRole)]) -> [ReferenceSurface] {
        measurements.map { measurement in
            let width = measurement.height * 3 / 2
            return roundedSurface(
                id: measurement.id,
                width: width,
                height: measurement.height,
                y: measurement.y,
                cornerRadius: measurement.height / 4,
                role: measurement.role,
            )
        }
    }

    private static let aspectSurfaces = [80, 120, 160, 240, 320].enumerated().map { index, width in
        roundedSurface(
            id: ["aspect-1", "aspect-1_5", "aspect-2", "aspect-3", "aspect-4"][index],
            width: CGFloat(width), height: 80, y: CGFloat(24 + index * 128), cornerRadius: 20,
            role: index.isMultiple(of: 2) ? .training : .holdout,
        )
    }

    private static let roundnessSurfaces = [0, 12, 24, 36, 48].enumerated().map { index, radius in
        roundedSurface(
            id: "roundness-\(radius)", width: 240, height: 96, y: CGFloat(24 + index * 128),
            cornerRadius: CGFloat(radius), role: index.isMultiple(of: 2) ? .training : .holdout,
        )
    }

    private static func roundedSurface(
        id: String, width: CGFloat, height: CGFloat, y: CGFloat, cornerRadius: CGFloat, role: CalibrationRole,
    ) -> ReferenceSurface {
        ReferenceSurface(
            id: id,
            frame: CGRect(x: (viewportSize.width - width) / 2, y: y, width: width, height: height),
            shape: .roundedRectangle(cornerRadius: cornerRadius),
            role: role,
        )
    }
}

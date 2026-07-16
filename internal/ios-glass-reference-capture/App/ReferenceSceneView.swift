import SwiftUI

struct ReferenceSceneView: View {
    let scene: CaptureScene

    var body: some View {
        ZStack(alignment: .topLeading) {
            background
            ForEach(ReferenceLayout.surfaces(for: scene.page)) { surface in
                glass(surface)
            }
        }
        .frame(
            width: ReferenceLayout.viewportSize.width,
            height: ReferenceLayout.viewportSize.height,
            alignment: .topLeading,
        )
        .clipped()
        .environment(\.colorScheme, scene.colorScheme)
    }

    private var background: some View {
        Canvas { context, size in
            context.fill(
                Path(CGRect(origin: .zero, size: size)),
                with: .color(scene.backgroundColor),
            )

            guard scene.isGrid else {
                return
            }

            let verticalLineColor: Color
            let horizontalLineColor: Color
            switch scene.colorScheme {
            case .light:
                verticalLineColor = Color(hex: 0x6B7A90)
                horizontalLineColor = Color(hex: 0x6B7A90)
            case .dark:
                verticalLineColor = .white.opacity(0.72)
                horizontalLineColor = .cyan.opacity(0.72)
            @unknown default:
                verticalLineColor = Color(hex: 0x6B7A90)
                horizontalLineColor = Color(hex: 0x6B7A90)
            }

            for x in stride(from: 0, through: size.width, by: 16) {
                var path = Path()
                path.move(to: CGPoint(x: x, y: 0))
                path.addLine(to: CGPoint(x: x, y: size.height))
                context.stroke(path, with: .color(verticalLineColor), lineWidth: 1)
            }

            for y in stride(from: 0, through: size.height, by: 16) {
                var path = Path()
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(path, with: .color(horizontalLineColor), lineWidth: 1)
            }
        }
    }

    @ViewBuilder
    private func glass(_ surface: ReferenceSurface) -> some View {
        switch surface.shape {
        case .capsule:
            glass(in: Capsule(), frame: surface.frame)
        case let .roundedRectangle(cornerRadius):
            glass(in: RoundedRectangle(cornerRadius: cornerRadius), frame: surface.frame)
        }
    }

    private func glass<S: Shape>(in shape: S, frame: CGRect) -> some View {
        Color.clear
            .frame(width: frame.width, height: frame.height)
            .glassEffect(.regular.tint(nil).interactive(false), in: shape)
            .offset(x: frame.minX, y: frame.minY)
    }
}

private extension Color {
    init(hex: Int) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1,
        )
    }
}

import Darwin
import SwiftUI
import UIKit

@main
struct CaptureApp: App {
    private let sceneResult: Result<CaptureScene, Error> = Result {
        try CaptureScene.parse(arguments: CommandLine.arguments)
    }

    init() {
        UIView.setAnimationsEnabled(false)
    }

    var body: some Scene {
        WindowGroup {
            switch sceneResult {
            case let .success(scene):
                CaptureRootView(scene: scene)
            case let .failure(error):
                Color.red.task {
                    fail(error)
                }
            }
        }
    }
}

private struct CaptureRootView: View {
    let scene: CaptureScene

    @State private var didWriteReadiness = false

    var body: some View {
        GeometryReader { proxy in
            ReferenceSceneView(scene: scene)
                .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
                .task {
                    guard !didWriteReadiness else {
                        return
                    }
                    didWriteReadiness = true

                    let frameWaiter = DisplayFrameWaiter()
                    await frameWaiter.wait(frames: CaptureReadinessPolicy.minimumDisplayFrames)

                    guard let window = UIApplication.shared.connectedScenes
                        .compactMap({ $0 as? UIWindowScene })
                        .flatMap(\.windows)
                        .first(where: \.isKeyWindow)
                    else {
                        fail(CaptureRuntimeError.missingWindow)
                    }

                    guard let windowScene = window.windowScene else {
                        fail(CaptureRuntimeError.missingWindow)
                    }

                    await ActiveSceneSettlingGate.wait(for: windowScene)
                    guard !Task.isCancelled else {
                        return
                    }

                    do {
                        let ready = CaptureReady.make(
                            scene: scene,
                            scale: window.screen.scale,
                            framebufferSize: window.screen.bounds.size,
                            safeAreaInsets: window.safeAreaInsets,
                        )
                        try ready.writeAtomically()
                    } catch {
                        fail(error)
                    }
                }
        }
        .ignoresSafeArea()
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
    }
}

enum CaptureReadinessPolicy {
    static let minimumDisplayFrames = 2
    static let minimumForegroundDuration = Duration.milliseconds(1_200)
}

@MainActor
private enum ActiveSceneSettlingGate {
    static func wait(for windowScene: UIWindowScene) async {
        while !Task.isCancelled {
            if windowScene.activationState != .foregroundActive {
                for await _ in NotificationCenter.default.notifications(
                    named: UIScene.didActivateNotification,
                    object: windowScene,
                ) {
                    break
                }
            }

            try? await Task.sleep(for: CaptureReadinessPolicy.minimumForegroundDuration)

            if windowScene.activationState == .foregroundActive {
                return
            }
        }
    }
}

private enum CaptureRuntimeError: Error {
    case missingWindow
}

private func fail(_ error: Error) -> Never {
    let message = "iOS Liquid Glass reference capture failed: \(error)\n"
    _ = message.withCString { fputs($0, stderr) }
    exit(EXIT_FAILURE)
}

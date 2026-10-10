// Throwaway iOS reference harness for #1438. Mirrors the Haze "Glass — Merge Prototype" sample:
// two 140×64 pt capsules over the same busy backdrop, a spacing slider (0–64 pt), dragging, and an
// Overlap/Separate toggle, so recordings can be compared with the Android prototype.

import SwiftUI

@main
struct MergeHarnessApp: App {
  var body: some Scene {
    WindowGroup { ContentView() }
  }
}

private let pillSize = CGSize(width: 140, height: 64)
private let stage = "stage"

enum ToggleMode: String, CaseIterable, Identifiable {
  // Moves pill B onto pill A with a spring, like the Android sample's toggle.
  case move = "Move"
  // Removes pill B and lets glassEffectID morph it into pill A, the system merge transition.
  case glassEffectID = "glassEffectID"
  var id: String { rawValue }
}

struct ContentView: View {
  @State private var spacing: CGFloat = 40
  // Pill centres, in points. The initial 16 pt gap is below the spacing, as on Android.
  @State private var a = CGPoint(x: 24 + pillSize.width / 2, y: 160 + pillSize.height / 2)
  @State private var b = CGPoint(x: 24 + pillSize.width * 1.5 + 16, y: 160 + pillSize.height / 2)
  @State private var overlapped = false
  @State private var mode = ToggleMode.move
  @Namespace private var namespace

  var body: some View {
    ZStack {
      Backdrop().ignoresSafeArea()

      GlassEffectContainer(spacing: spacing) {
        ZStack {
          pill("A", id: "a").position(a)
          if !(mode == .glassEffectID && overlapped) {
            pill("B", id: "b").position(b)
          }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
      }

      // Drag handles sit above the container so its glass views stay untouched by hit testing.
      ZStack {
        dragHandle(id: "a") { a = $0 }.position(a)
        if !(mode == .glassEffectID && overlapped) {
          dragHandle(id: "b") { b = $0 }.position(b)
        }
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .coordinateSpace(.named(stage))

      VStack {
        // The live gap between the pills, so recorded frames can be measured without timestamps.
        Text(String(format: "gap %.1f pt", gap))
          .font(.system(size: 20, weight: .bold).monospacedDigit())
          .foregroundStyle(.white)
          .padding(6)
          .background(.black, in: .rect(cornerRadius: 6))
          .padding(.top, 260)
        Spacer()
        controls
      }
    }
  }

  /// Positive while apart, negative as deep as the pills overlap, as in the Android sample.
  private var gap: CGFloat {
    max(abs(b.x - a.x) - pillSize.width, abs(b.y - a.y) - pillSize.height)
  }

  private func pill(_ label: String, id: String) -> some View {
    Text(label)
      .font(.headline)
      .foregroundStyle(.white)
      .frame(width: pillSize.width, height: pillSize.height)
      .glassEffect(.regular, in: .capsule)
      .glassEffectID(id, in: namespace)
      .accessibilityIdentifier("merge_pill_\(id)")
  }

  private func dragHandle(id: String, onMove: @escaping (CGPoint) -> Void) -> some View {
    Color.clear
      .frame(width: pillSize.width, height: pillSize.height)
      .contentShape(.rect)
      .gesture(DragGesture(minimumDistance: 1, coordinateSpace: .named(stage)).onChanged { onMove($0.location) })
      .accessibilityIdentifier("merge_handle_\(id)")
  }

  private var controls: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack {
        Picker("Toggle", selection: $mode) {
          ForEach(ToggleMode.allCases) { Text($0.rawValue).tag($0) }
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("merge_toggle_mode")

        Button(overlapped ? "Separate" : "Overlap") { toggle() }
          .buttonStyle(.borderedProminent)
          .accessibilityIdentifier("merge_split_toggle")
      }
      HStack {
        Button("Run script") { Task { await runScript() } }
          .buttonStyle(.bordered)
          .accessibilityIdentifier("merge_run_script")
        Button("Threshold") { Task { await runThreshold() } }
          .buttonStyle(.bordered)
          .accessibilityIdentifier("merge_run_threshold")
      }
      Text("Spacing: \(Int(spacing)) pt").foregroundStyle(.white)
      Slider(value: $spacing, in: 0...64)
        .accessibilityIdentifier("merge_spacing")
    }
    .padding(16)
    .background(.black.opacity(0.7), in: .rect(cornerRadius: 16))
    .padding(16)
  }

  /// Replays the Android demo's gestures without touch injection: B is moved every frame without
  /// animation, as a finger drag would, then both toggle modes run.
  @MainActor
  private func runScript() async {
    mode = .move
    overlapped = false
    a = CGPoint(x: 24 + pillSize.width / 2, y: 160 + pillSize.height / 2)
    b = CGPoint(x: 24 + pillSize.width * 1.5 + 16, y: 160 + pillSize.height / 2)
    try? await Task.sleep(for: .seconds(1.5))
    await drag(to: CGPoint(x: b.x + 93, y: b.y), seconds: 5)    // slow pull
    try? await Task.sleep(for: .seconds(1.5))
    await drag(to: CGPoint(x: b.x - 84, y: b.y), seconds: 0.45) // quick push back
    try? await Task.sleep(for: .seconds(2))
    await drag(to: CGPoint(x: b.x, y: b.y + 170), seconds: 0.25) // fast pull down
    try? await Task.sleep(for: .seconds(2))
    await drag(to: CGPoint(x: b.x, y: b.y - 132), seconds: 0.9)  // back into range
    try? await Task.sleep(for: .seconds(2))
    for toggleMode in [ToggleMode.move, .glassEffectID] {
      mode = toggleMode
      for _ in 0..<2 {
        toggle()
        try? await Task.sleep(for: .seconds(2.5))
      }
    }
  }

  /// Measures the join and break: opens the gap from 0 to 40 pt at 10 pt/s, then snaps it from 40
  /// to 4 pt in 0.2 s, then holds so any settle is visible.
  @MainActor
  private func runThreshold() async {
    mode = .move
    overlapped = false
    let row = 160 + pillSize.height / 2
    a = CGPoint(x: 24 + pillSize.width / 2, y: row)
    b = CGPoint(x: 24 + pillSize.width * 1.5, y: row)
    try? await Task.sleep(for: .seconds(1.5))
    await drag(to: CGPoint(x: b.x + 40, y: row), seconds: 4)
    try? await Task.sleep(for: .seconds(1.5))
    await drag(to: CGPoint(x: b.x - 36, y: row), seconds: 0.2)
    try? await Task.sleep(for: .seconds(2))
  }

  @MainActor
  private func drag(to target: CGPoint, seconds: Double) async {
    let start = b
    let frames = max(Int(seconds * 60), 1)
    for frame in 1...frames {
      let t = CGFloat(frame) / CGFloat(frames)
      b = CGPoint(x: start.x + (target.x - start.x) * t, y: start.y + (target.y - start.y) * t)
      try? await Task.sleep(for: .milliseconds(16))
    }
  }

  private func toggle() {
    switch mode {
    case .move:
      // Fully overlap pill A, or separate vertically by more than the largest spacing.
      let target = overlapped ? CGPoint(x: a.x, y: a.y + pillSize.height + 80) : a
      overlapped.toggle()
      withAnimation(.spring) { b = target }
    case .glassEffectID:
      if overlapped { b = CGPoint(x: a.x, y: a.y + pillSize.height + 80) }
      withAnimation(.spring) { overlapped.toggle() }
    }
  }
}

/// The Android sample's backdrop: a 24 pt checker, orange diagonal stripes, and blue text rows.
struct Backdrop: View {
  var body: some View {
    ZStack {
      Canvas { context, size in
        let cell: CGFloat = 24
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.067, green: 0.094, blue: 0.153)))
        for row in 0..<Int(ceil(size.height / cell)) {
          for column in 0..<Int(ceil(size.width / cell)) where (row + column) % 2 == 0 {
            context.fill(
              Path(CGRect(x: CGFloat(column) * cell, y: CGFloat(row) * cell, width: cell, height: cell)),
              with: .color(Color(red: 0.898, green: 0.906, blue: 0.922)),
            )
          }
        }
        var x = -size.height
        while x < size.width {
          var stripe = Path()
          stripe.move(to: CGPoint(x: x, y: 0))
          stripe.addLine(to: CGPoint(x: x + size.height, y: size.height))
          context.stroke(stripe, with: .color(Color(red: 0.976, green: 0.451, blue: 0.086)), lineWidth: cell / 3)
          x += cell * 2
        }
      }
      VStack {
        ForEach(0..<8, id: \.self) { _ in
          Spacer()
          Text("HAZE GLASS MERGE")
            .font(.system(size: 32, weight: .black))
            .foregroundStyle(Color(red: 0.220, green: 0.741, blue: 0.973))
        }
        Spacer()
      }
    }
  }
}

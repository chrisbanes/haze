// Open-question scenes for #1409, launched with `-scenario <name>` and `-joined YES|NO`.
// Every scene uses the same backdrop and fixed positions, so joined and apart screenshots line up.
//
//   tint:        regular blue-tinted pill A beside a plain regular pill B
//   variant:     clear pill A beside regular pill B
//   unequal:     a 300×180 card above a 140×64 pill
//   unequalSeparate: the same card and pill in two sibling containers (control for `unequal`)
//   cardToggle:  Toggle inserts/removes the card while the pill stays apart (`-joined NO`)
//   clearWithCard / clearAlone: a clear pill with or without a regular card in its container
//   interactive: two `.interactive()` pills; long-press one to see whether the merged shape follows
//   nested:      pill B in its own GlassEffectContainer nested inside A's container
//   siblings:    A and B in two sibling GlassEffectContainers
//   far:         B is inserted and removed with glassEffectID while beyond the spacing
//   union / unionControl: A and B with and without glassEffectUnion; unionSide offsets B diagonally
//   unionShapes: a card unioned with a pill (`-swap YES` declares the pill first)
//   unionTint:   tinted A unioned with plain B (`-swap YES` declares B first); unionClear: clear A with regular B
//   unionNeighbour: unioned A and B, plus plain pill C 8 pt right of A
//   unionToggle: Toggle inserts or removes unioned B; unionOptics: one capsule with the union's bounds
//   unionInteractive: two unioned `.interactive()` pills; long-press A (PressTests.testPressUnion)
//
// `joined` puts the members 8 pt apart, well inside the 40 pt spacing (bridges below 20 pt).
// Apart moves B 240 pt further down, well outside it. The Toggle button animates between them.

import SwiftUI

enum Scenario: String, CaseIterable {
  case tint, variant, unequal, unequalSeparate, cardToggle, clearWithCard, clearAlone, clearCard, interactive, nested, siblings, far, union, unionControl, unionSide,
    unionShapes, unionTint, unionClear, unionNeighbour, unionToggle, unionOptics, unionInteractive
}

private let spacing: CGFloat = 40
private let pill = CGSize(width: 140, height: 64)
private let card = CGSize(width: 300, height: 180)
private let joinedGap: CGFloat = 8
private let apartOffset: CGFloat = 240

struct ScenarioView: View {
  let scenario: Scenario
  // A launch argument arrives as the string "YES"/"NO", which `bool(forKey:)` parses.
  @State private var joined = UserDefaults.standard.object(forKey: "joined") == nil || UserDefaults.standard.bool(forKey: "joined")
  @State private var present = true
  @Namespace private var namespace
  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

  var body: some View {
    ZStack {
      Backdrop().ignoresSafeArea()
      scene.frame(maxWidth: .infinity, maxHeight: .infinity)
      VStack {
        Spacer()
        HStack {
          // RM/RT confirm the simulator's accessibility settings reached the app.
          Text("\(scenario.rawValue) · \(joined ? "joined" : "apart")\(present ? "" : " · B removed")\(reduceMotion ? " · RM" : "")\(reduceTransparency ? " · RT" : "")")
            .font(.headline.monospaced())
            .foregroundStyle(.white)
          Spacer()
          Button("Toggle") { toggle() }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("scenario_toggle")
        }
        .padding(16)
        .background(.black.opacity(0.7), in: .rect(cornerRadius: 16))
        .padding(16)
      }
    }
    // `-autotoggle YES` toggles every 2.5 s, so transitions can be recorded without touch injection.
    .task {
      guard UserDefaults.standard.bool(forKey: "autotoggle") else { return }
      while !Task.isCancelled {
        try? await Task.sleep(for: .seconds(2.5))
        toggle()
      }
    }
  }

  private func toggle() {
    withAnimation(.spring) {
      if scenario == .far || scenario == .cardToggle || scenario == .unionToggle { present.toggle() } else { joined.toggle() }
    }
  }

  // Member A's top-left corner; B sits below A.
  private let origin = CGPoint(x: 40, y: 140)

  private func bY(aHeight: CGFloat, bHeight: CGFloat) -> CGFloat {
    origin.y + aHeight + joinedGap + bHeight / 2 + (joined ? 0 : apartOffset)
  }

  private var aCentre: CGPoint { CGPoint(x: origin.x + pill.width / 2, y: origin.y + pill.height / 2) }
  private var bCentre: CGPoint { CGPoint(x: origin.x + pill.width / 2, y: bY(aHeight: pill.height, bHeight: pill.height)) }

  @ViewBuilder private var scene: some View {
    switch scenario {
    case .tint:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A blue", .regular.tint(.blue)).position(aCentre)
          member("B", .regular).position(bCentre)
        }
      }
    case .variant:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A clear", .clear).position(aCentre)
          member("B regular", .regular).position(bCentre)
        }
      }
    case .unequal:
      // Card A is centred on the same column as the pills in other scenes, so its edges cross the stripes.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          Text("A card").font(.headline).foregroundStyle(.white)
            .frame(width: card.width, height: card.height)
            .glassEffect(.regular, in: .rect(cornerRadius: 36))
            .position(x: origin.x + card.width / 2, y: origin.y + card.height / 2)
          member("B", .regular)
            .position(x: origin.x + pill.width / 2, y: bY(aHeight: card.height, bHeight: pill.height))
        }
      }
    case .unequalSeparate:
      // Control for `unequal`: the same card and pill, each in its own container.
      ZStack {
        GlassEffectContainer(spacing: spacing) {
          Text("A card").font(.headline).foregroundStyle(.white)
            .frame(width: card.width, height: card.height)
            .glassEffect(.regular, in: .rect(cornerRadius: 36))
            .position(x: origin.x + card.width / 2, y: origin.y + card.height / 2)
        }
        GlassEffectContainer(spacing: spacing) {
          member("B", .regular)
            .position(x: origin.x + pill.width / 2, y: bY(aHeight: card.height, bHeight: pill.height))
        }
      }
    case .cardToggle:
      // Toggle inserts or removes the card while the pill stays 240 pt below it, to see whether the pill's
      // container-wide optics animate or snap.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          if present {
            Text("A card").font(.headline).foregroundStyle(.white)
              .frame(width: card.width, height: card.height)
              .glassEffect(.regular, in: .rect(cornerRadius: 36))
              .position(x: origin.x + card.width / 2, y: origin.y + card.height / 2)
          }
          member("B", .regular)
            .position(x: origin.x + pill.width / 2, y: bY(aHeight: card.height, bHeight: pill.height))
        }
      }
    case .clearWithCard:
      // A regular card and a clear pill share a container; they can't merge. Does the card still size the pill?
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          Text("A card").font(.headline).foregroundStyle(.white)
            .frame(width: card.width, height: card.height)
            .glassEffect(.regular, in: .rect(cornerRadius: 36))
            .position(x: origin.x + card.width / 2, y: origin.y + card.height / 2)
          member("B clear", .clear)
            .position(x: origin.x + pill.width / 2, y: bY(aHeight: card.height, bHeight: pill.height))
        }
      }
    case .clearAlone:
      // Control for `clearWithCard`: the same clear pill in its own container.
      GlassEffectContainer(spacing: spacing) {
        member("B clear", .clear)
          .position(x: origin.x + pill.width / 2, y: bY(aHeight: card.height, bHeight: pill.height))
      }
    case .clearCard:
      // Is `.clear` size-responsive at all? A clear card alone, compared with `clearAlone`.
      GlassEffectContainer(spacing: spacing) {
        Text("A clear card").font(.headline).foregroundStyle(.white)
          .frame(width: card.width, height: card.height)
          .glassEffect(.clear, in: .rect(cornerRadius: 36))
          .position(x: origin.x + card.width / 2, y: origin.y + card.height / 2)
      }
    case .interactive:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A", .regular.interactive()).position(aCentre)
          member("B", .regular.interactive()).position(bCentre)
        }
      }
    case .nested:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A outer", .regular).position(aCentre)
          GlassEffectContainer(spacing: spacing) {
            member("B inner", .regular).position(bCentre)
          }
        }
      }
    case .siblings:
      ZStack {
        GlassEffectContainer(spacing: spacing) {
          member("A", .regular).position(aCentre)
        }
        GlassEffectContainer(spacing: spacing) {
          member("B", .regular).position(bCentre)
        }
      }
    case .union, .unionControl:
      // A and B share glassEffectUnion (or not, for the control); `-joined NO` puts them 240 pt beyond the spacing.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          unioned(member("A", .regular)).position(aCentre)
          unioned(member("B", .regular)).position(bCentre)
        }
      }
    case .unionSide:
      // Unioned pills offset diagonally and far apart, to see whether the union fills between them.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A", .regular).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
          member("B", .regular).glassEffectUnion(id: "u", namespace: namespace)
            .position(x: aCentre.x + 180, y: bCentre.y)
        }
      }
    case .unionShapes:
      // A 300×180 rounded-rect card unioned with a capsule pill; `-swap YES` declares the pill first.
      GlassEffectContainer(spacing: spacing) {
        let cardView = Text("A card").font(.headline).foregroundStyle(.white)
          .frame(width: card.width, height: card.height)
          .glassEffect(.regular, in: .rect(cornerRadius: 36))
          .glassEffectUnion(id: "u", namespace: namespace)
          .position(x: origin.x + card.width / 2, y: origin.y + card.height / 2)
        let pillView = member("B pill", .regular).glassEffectUnion(id: "u", namespace: namespace)
          .position(x: origin.x + pill.width / 2, y: bY(aHeight: card.height, bHeight: pill.height))
        ZStack {
          if UserDefaults.standard.bool(forKey: "swap") { pillView; cardView } else { cardView; pillView }
        }
      }
    case .unionTint:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          // `-swap YES` declares plain B before tinted A.
          if UserDefaults.standard.bool(forKey: "swap") {
            member("B", .regular).glassEffectUnion(id: "u", namespace: namespace).position(bCentre)
            member("A blue", .regular.tint(.blue)).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
          } else {
            member("A blue", .regular.tint(.blue)).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
            member("B", .regular).glassEffectUnion(id: "u", namespace: namespace).position(bCentre)
          }
        }
      }
    case .unionClear:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A clear", .clear).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
          member("B regular", .regular).glassEffectUnion(id: "u", namespace: namespace).position(bCentre)
        }
      }
    case .unionNeighbour:
      // A and B are unioned; plain pill C sits 8 pt right of A, inside the spacing of the union's bounds.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A", .regular).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
          member("B", .regular).glassEffectUnion(id: "u", namespace: namespace).position(bCentre)
          member("C", .regular).position(x: aCentre.x + pill.width + joinedGap, y: aCentre.y)
        }
      }
    case .unionToggle:
      // Toggle inserts or removes unioned B, to see whether the union's bounds animate.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A", .regular).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
          if present {
            member("B", .regular).glassEffectUnion(id: "u", namespace: namespace).position(bCentre)
          }
        }
      }
    case .unionOptics:
      // Control for `union -joined NO`: one plain capsule with the union's bounds.
      GlassEffectContainer(spacing: spacing) {
        let top = origin.y
        let bottom = bCentre.y + pill.height / 2
        Text("single").font(.headline).foregroundStyle(.white)
          .frame(width: pill.width, height: bottom - top)
          .glassEffect(.regular, in: .capsule)
          .position(x: aCentre.x, y: (top + bottom) / 2)
      }
    case .unionInteractive:
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A", .regular.interactive()).glassEffectUnion(id: "u", namespace: namespace).position(aCentre)
          member("B", .regular.interactive()).glassEffectUnion(id: "u", namespace: namespace).position(bCentre)
        }
      }
    case .far:
      // B is apart (beyond the spacing) unless `-joined YES`; Toggle inserts or removes it with no explicit transition.
      GlassEffectContainer(spacing: spacing) {
        ZStack {
          member("A", .regular).glassEffectID("a", in: namespace).position(aCentre)
          if present {
            switch UserDefaults.standard.string(forKey: "transition") {
            case "materialize":
              member("B", .regular).glassEffectID("b", in: namespace).glassEffectTransition(.materialize).position(bCentre)
            case "matched":
              member("B", .regular).glassEffectID("b", in: namespace).glassEffectTransition(.matchedGeometry).position(bCentre)
            default:
              member("B", .regular).glassEffectID("b", in: namespace).position(bCentre)
            }
          }
        }
      }
    }
  }

  @ViewBuilder private func unioned(_ view: some View) -> some View {
    if scenario == .union { view.glassEffectUnion(id: "u", namespace: namespace) } else { view }
  }

  private func member(_ label: String, _ glass: Glass) -> some View {
    Text(label)
      .font(.headline)
      .foregroundStyle(.white)
      .frame(width: pill.width, height: pill.height)
      .glassEffect(glass, in: .capsule)
  }
}

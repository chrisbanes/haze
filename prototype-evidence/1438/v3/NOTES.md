# #1409 open questions: iOS reference, v3

These notes check the #1409 API decisions against iOS. They cover the questions the v2 harness left open: mixed
tints and variants, unequal member sizes, `.interactive()` presses, nested and sibling containers, transitions
beyond the spacing, and accessibility settings.

## Setup

- **Environment:** Xcode 27.0 (27A266a), iOS 27.0 simulator runtime (24A434), and the "Haze iOS 27 Reference"
  device. The iOS 26 runtime is not installed, so nothing here was checked on iOS 26 or on a physical device.
- **Harness:** `../ios-harness`. Scenes live in `Sources/Scenarios.swift` and launch with
  `-scenario <name> -joined YES|NO`. `-autotoggle YES` re-runs a transition every 2.5 s, and `-transition` picks
  the transition in the `far` scene.
- **Geometry:** every scene uses 40 pt spacing, so bridges form below a 20 pt gap. Joined members are 8 pt apart.
  Apart moves member B 240 pt further down.
- **Presses:** `UITests/PressTests.swift` holds a 3 s press while the screen records, because `simctl` cannot
  inject touches.
- **Accessibility:** Reduce Motion and Reduce Transparency were set through `com.apple.Accessibility` defaults,
  and Increase Contrast through `simctl ui`. The scene's status bar prints `RM` and `RT` when SwiftUI reports
  them. All settings were restored afterwards.
- **Folders:** `ios/pairs/` holds joined (left) and apart (right) crops. `ios/sheets/` holds transition frames
  at 30 fps, 0.33 s apart from left to right.

## Findings

| Question | Observed on iOS 27 simulator | Evidence |
| --- | --- | --- |
| Do members with different tints merge? | **Yes.** The bridge forms, and each member keeps its own tint. Tint blends softly across the neck. | `pairs/tint_pair.png` |
| Do `.clear` and `.regular` members merge? | **No.** There is no bridge at an 8 pt gap; they stay separate shapes. | `pairs/variant_pair.png` |
| How are optics sized for unequal members? | **Per container, not per merged shape.** A 140×64 pill in the same container as a 300×180 card gets the card's heavy diffusion, both when merged and when 240 pt away. In its own container, at the same positions, it keeps pill optics. Two equal pills in one container keep pill optics, so container bounds are not the input. This fits sizing from the largest member. | `unequal_pill_comparison.png` (merged; same container, apart; own container, near; own container, apart), `pairs/unequal*_pair.png` |
| Do container-wide optics animate when a large member comes or goes? | **No, they snap.** While a card is inserted and removed 240 pt away, the pill's diffusion switches between the last frame with the card (t ≈ 0.65 s) and the first frame of the removal (t ≈ 1.82 s). The recording is variable-frame-rate and captured no frames in between, because nothing changed. The card's own glass still animates out. | `sheets/card_toggle_pill_timeline.png` (5 fps), `sheets/card_remove_pill_frames.png` (every recorded frame), `card_toggle.mp4` |
| Does a member of another variant share the container's size? | **Inconclusive.** A `.clear` pill looks the same with or without a `.regular` card in its container. However, a `.clear` card is as lightly blurred as a `.clear` pill, so `.clear` barely responds to size and can't reveal the coupling. | `clear_pill_comparison.png` (with card: merged, apart; alone: near, far), `clear_vs_regular_card.png` |
| Does a press deform the merged shape? | **Yes.** The pressed pill scales up by about 10%, the merged outline and neck follow, and the other member keeps its size. The brightening is concentrated on the pressed member. | `interactive_press_joined_rest_vs_pressed.png`, `sheets/press_joined.png`, `interactive_press_joined.mp4` |
| Removal and insertion within the spacing | **Matched geometry.** A removed member retracts into its neighbour, and an inserted one grows out of it, over about 0.3 s. | `sheets/near_remove.png`, `sheets/near_insert.png`, `near_default.mp4` |
| Removal beyond the spacing | **Dematerializes in place.** The glass blurs out in about 0.15 s and doesn't move towards A. An explicit `.glassEffectTransition(.matchedGeometry)` looks the same as the default. | `sheets/far_default_remove.png`, `sheets/far_matched_remove.png`, `far_*.mp4` |
| Nested containers | **No cross-merge.** A member of an inner container does not bridge to a member of the outer one at an 8 pt gap. | `pairs/nested_pair.png` |
| Sibling containers | **No merge.** | `pairs/siblings_pair.png` |
| Reduce Motion | **Not snapped.** The matched-geometry retract and grow still animate with `RM` reported. | `reduce-motion/near_default.mp4`, `reduce-motion/near_remove_sheet.png` |
| Reduce Transparency | **Merging unchanged.** The material becomes nearly opaque. The bridge and tint blend remain. | `reduce-transparency/tint_pair.png` |
| Increase Contrast | **Merging unchanged.** One border traces the merged outline. | `increase-contrast/tint_pair.png` |

## Against the #1409 decisions

These comparisons were made against the first draft of the decisions. ADR-0013
([#1442](https://github.com/chrisbanes/haze/pull/1442)) adopts the iOS behaviour for each mismatch. Colours
blend while all other values must match. Optics are sized per container from the largest member and change
immediately. Reduce Motion keeps the morph without overshoot, and press lighting is per member.

- **Merge rule (members merge only when material values are equal): differs.** iOS merges different tints and
  blends them across the bridge. It refuses to merge different variants. The closest Haze rule merges members
  with equal optics, input and performance mode, and blends tint and colour per member.
- **Optics sizing (smallest member, per cluster): differs.** iOS resolves one size per container, apparently
  from the largest member, whether or not the members are merged. That matches Apple's description of a
  container enforcing "a uniform adaptation" (WWDC25 session 284).
- **Per-member press transform: matches.** The lighting decision (per container, following the most recently
  pressed member) is visually close, but iOS concentrates the brightening on the pressed member.
- **Transitions (MatchedGeometry within the spacing, Materialize beyond): match.** Beyond the spacing, iOS
  dematerializes even when `.matchedGeometry` is requested.
- **Reduced motion snaps transitions: differs.** iOS keeps animating the morph. Apple says Reduce Motion
  "disables elastic properties", which these recordings can't separate from the spring.
- **Nearest container wins: matches.**
- **Accessibility materials: no merge-specific behaviour.** Merging is unchanged under Reduce Transparency and
  Increase Contrast.

## Limits

- Simulator only, on iOS 27. Session 269 (WWDC26) changes the Liquid Glass look, so iOS 26 may differ.
- One geometry per question. The largest-member sizing rule is inferred from three layouts, not measured.
- Press lighting was judged from frames. The pressed and resting states were not compared as pixel differences.

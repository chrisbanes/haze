# #1409 `glassEffectUnion`: iOS reference, v4

These notes check how iOS `glassEffectUnion(id:namespace:)` behaves, to decide whether Haze should copy it, and how.
The question came from replacing `hazeGlassContainer(mergeDistance)` with a policy type that could also merge by tag.

## Setup

- **Environment:** Xcode 27.0 (27A266a), iOS 27.0 simulator runtime (24A434), iPhone 17 Pro simulator. Nothing
  here was checked on iOS 26 or on a physical device.
- **Harness:** `../ios-harness`, with the `union*` scenes in `Sources/Scenarios.swift`. They launch with
  `-scenario <name> -joined YES|NO`, as in v3.
- **Geometry:** 40 pt spacing, 140×64 pt capsules and a 300×180 pt card with 36 pt corners. Joined members are 8 pt
  apart. Apart moves member B 240 pt further down, far beyond the spacing.
- **Animation:** `unionToggle` and `union` were recorded with `-autotoggle YES`; the toggle uses
  `withAnimation(.spring)`. `sheets/remove_insert_frames.png` shows frames 0.05 s apart (removal on top, insertion
  below). `sheets/move_frames.png` shows frames 0.2 s apart.
- **Press:** `PressTests.testPressUnion` holds a 3 s press on pill A while the screen records.
  `sheets/press_frames.png` shows frames 0.5 s apart; the bottom row's first six frames are during the press.

## Findings

| Question | Observed on iOS 27 simulator | Evidence |
| --- | --- | --- |
| What does a union of far-apart members draw? | **One shape over the members' combined bounding box**, not a bridge and not the geometric union. Stacked pills become one tall capsule; diagonal pills become one large rounded shape covering empty space. | `union_apart.png`, `union_diagonal_apart.png`, `control_apart.png` |
| Does distance matter? | **No.** At an 8 pt gap the union is also one shape over the bounds, replacing the spacing bridge. | `union_joined.png` |
| Which shape is used for members with different shapes? | **The first-declared member's.** Card first gives a rounded rect over the bounds; pill first gives a capsule. | `shapes_card_first.png`, `shapes_pill_first.png`, `shapes_joined.png`, `sheets/shapes.png` |
| Which style is used for different tints? | **The first-declared member's.** Tinted A first tints the whole union, B included. Plain B first gives a plain union, and A loses its tint. | `tint_tinted_first.png`, `tint_plain_first.png` |
| Do `.clear` and `.regular` members union? | **No.** They stay two separate pills. | `clear_with_regular.png` |
| Does a union bridge with a nearby non-union member? | **Yes, as one member.** Plain pill C, 8 pt right of A, joins the union capsule with the usual spacing neck. | `neighbour.png`, `sheets/styles_neighbour.png` |
| Do the bounds animate when a member is added or removed? | **Yes.** The union grows from A down to B and shrinks back over roughly 0.3 s with the caller's spring. Moving B reshapes it continuously. | `union_remove_insert.mp4`, `union_move.mp4`, `sheets/remove_insert_frames.png`, `sheets/move_frames.png` |
| How are optics sized? | **From the union's shape.** The union looks the same as one plain capsule with the same bounds, and much more diffused than separate pills. | `optics_union.png`, `optics_single_capsule.png`, `sheets/optics.png` |
| What does `.interactive()` do on a unioned member? | **The whole union reacts.** Pressing A brightens and slightly enlarges the entire shape, including B's end, and it settles on release. | `union_press.mp4`, `sheets/press_frames.png` |

## Limits

- The 0.3 s timing and the press growth are read from sampled frames, so they are approximate.
- v3 found that optics are sized per container from the largest member. It follows that a union probably raises the
  optical size of other members in its container, but no scene here isolates that.
- A union's own spacing merges were checked only with a plain neighbour, not with another union.


## Snap positions

Snapper supports the customization of where items snap to. By default Snapper will snap
items to the center of the layout container, but you can provide your own 'snap offset' via
the `snapOffsetForItem` parameters.

`snapOffsetForItem` is a parameter which takes a block in the form of `(layoutInfo: SnapperLayoutInfo, item: SnapperLayoutItemInfo) -> Int`,
and allows apps to supply custom logic of where to snap each individual item.

A number of predefined values are supplied in the [SnapOffsets](../api/lib/dev.chrisbanes.haze/-snap-offsets/) class,
for snapping items to the start, center and end.

``` kotlin
LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(
        lazyListState = lazyListState,
        snapOffsetForItem = SnapOffsets.Start,
    ),
) {
    // content
}
```

## Finding the 'current' item

Most of the time apps will probably use the short-hand convenience function:
`rememberSnapperFlingBehavior(LazyListState)`, but there are times when
it is useful to get access to the `SnapperLayoutInfo`.

SnapperLayoutInfo provides lots of information about the 'snapping state' of
the scrollable container, and provides access to the 'current item'.

For example, if you wish to invoke some action when a fling + snap has finished
you can do the following:

``` kotlin
val lazyListState = rememberLazyListState()
val layoutInfo = rememberLazyListSnapperLayoutInfo(lazyListState)

LaunchedEffect(lazyListState.isScrollInProgress) {
    if (!lazyListState.isScrollInProgress) {
        // The scroll (fling) has finished, get the current item and
        // do something with it!
        val snappedItem = layoutInfo.currentItem
        // TODO: do something with snappedItem
    }
}

LazyColumn(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(layoutInfo),
) {
    // content
}
```

## Customization of the target index

The `snapIndex` parameter allows customization of the index which Snapper which fling to
after a user has started a fling.

The block is given the [SnapperLayoutInfo][snapperlayoutinfo], the index where the fling started, and
with the index which Snapper has determined is the correct index to fling, without the layout limits.
The block should return the index which Snapper should fling and snap to.

The following are some examples of what you can achieve with `snapIndex`.

### Controlling the maximum fling distance

The following example sets the `snapIndex` so that the user can only fling up a maximum of 3 items:

``` kotlin
val MaxItemFling = 3

LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(
        lazyListState = lazyListState,
        snapIndex = { layoutInfo, startIndex, targetIndex ->
            targetIndex.coerceIn(startIndex - MaxItemFling, startIndex + MaxItemFling)
        }
    ),
) {
    // content
}
```

### Snapping groups

The `snapIndex` parameter can also be used to achieve snapping to 'groups' of items.

The following example provide a `snapIndex` block which snaps flings to groups of 3 items:

``` kotlin
val GroupSize = 3

LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(
        lazyListState = lazyListState,
        snapIndex = { _, _, targetIndex ->
            val mod = targetIndex % GroupSize
            if (mod > (GroupSize / 2)) {
                // Round up towards infinity
                GroupSize + targetIndex - mod
            } else {
                // Round down towards zero
                targetIndex - mod
            }
    ),
) {
    // content
}
```

## Animation specs

SnapperFlingBehavior allows setting of two different animation specs: `decayAnimationSpec` and `springAnimationSpec`.

- `decayAnimationSpec` is the main spec used for flinging, and is used when the fling has enough velocity to scroll past
the current item.
- `springAnimationSpec` is used when there is not enough velocity to fling using `decayAnimationSpec`, and instead 'snaps'
to the current item.

Both of the specs can be customized to apps wishes.

  [snapperlayoutinfo]: ../api/lib/dev.chrisbanes.haze/-snapper-layout-info/
  [rememberlazylistsnapperlayoutinfo]: ../api/lib/dev.chrisbanes.haze/remember-lazy-list-snapper-layout-info.html

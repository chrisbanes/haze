// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.liquidglass.LiquidGlassVisualEffect
import haze_root.haze_screenshot_tests.generated.resources.Res
import haze_root.haze_screenshot_tests.generated.resources.photo
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CreditCardSample(
  visualEffect: VisualEffect,
  backgroundColors: List<Color> = listOf(Color.Blue, Color.Cyan),
  shape: RoundedCornerShape = RoundedCornerShape(16.dp),
  enabled: Boolean = true,
  numberCards: Int = 1,
) {
  val hazeState = remember { HazeState() }

  Box {
    // Background content
    CreditCardBackground(
      backgroundColors = backgroundColors,
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState, zIndex = 0f),
    )

    repeat(numberCards) { index ->
      // Our card
      val reverseIndex = (numberCards - 1 - index)

      CreditCard(
        reverseIndex = reverseIndex,
        hazeState = hazeState,
        index = index,
        shape = shape,
        enabled = enabled,
        visualEffect = visualEffect,
        modifier = Modifier
          .align(Alignment.Center),
      )
    }
  }
}

@Composable
internal fun CreditCardContentBlurring(
  visualEffect: VisualEffect,
  backgroundColors: List<Color> = listOf(Color.Blue, Color.Cyan),
  drawContentBehind: Boolean = false,
) {
  Box(Modifier.background(backgroundColors.first())) {
    // Background content
    CreditCardBackground(
      backgroundColors = backgroundColors,
      modifier = Modifier
        .fillMaxSize()
        .hazeEffect {
          this.drawContentBehind = drawContentBehind
          this.visualEffect = visualEffect
        },
    )
  }
}

@Composable
internal fun SourceTransitionSample(
  visualEffect: VisualEffect,
  showSource: Boolean,
) {
  val hazeState = rememberHazeState()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    if (showSource) {
      CreditCardBackground(
        backgroundColors = listOf(Color(0xFF1E88E5), Color(0xFFE91E63)),
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(state = hazeState, zIndex = 0f),
      )
    }

    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .height(176.dp)
        .hazeEffect(state = hazeState) {
          this.visualEffect = visualEffect
        },
    ) {
      Text(
        text = "Transition",
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(horizontal = 32.dp),
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StickyHeaderListSample(
  visualEffect: VisualEffect,
) {
  val hazeState = rememberHazeState()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    stickyHeader {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(104.dp)
          .hazeEffect(state = hazeState) {
            inputScale = HazeInputScale.Auto
            this.visualEffect = visualEffect
          },
      ) {
        Text(
          text = "Header",
          color = Color.White,
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(horizontal = 32.dp),
        )
      }
    }

    items(StickyHeaderRows) { colors ->
      Row(
        modifier = Modifier
          .hazeSource(hazeState)
          .fillMaxWidth()
          .height(144.dp)
          .background(Color.Black),
      ) {
        colors.forEach { color ->
          Box(
            modifier = Modifier
              .fillMaxHeight()
              .weight(1f)
              .background(color),
          )
        }
      }
    }
  }
}

@Composable
internal fun CreditCardPagerSample(
  visualEffect: VisualEffect,
  pagerPosition: Float,
  backgroundColors: List<Color> = listOf(Color.Blue, Color.Cyan),
  shape: RoundedCornerShape = RoundedCornerShape(16.dp),
  enabled: Boolean = true,
  numberCards: Int = 2,
) {
  val hazeState = remember { HazeState() }

  Box {
    // Background content
    CreditCardBackground(
      backgroundColors = backgroundColors,
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState, zIndex = 0f),
    )
    val positionIndex = pagerPosition.roundToInt()
    val pagerState = PagerState(positionIndex, pagerPosition - positionIndex) {
      numberCards
    }

    HorizontalPager(
      pagerState,
      pageSize = PageSize.Fixed(275.dp),
      modifier = Modifier.align(Alignment.Center),
    ) { index ->
      // Our card
      CreditCard(
        reverseIndex = 0,
        hazeState = hazeState,
        index = index,
        shape = shape,
        enabled = enabled,
        visualEffect = visualEffect,
        baseWidth = 0.9f,
      )
    }
  }
}

@Composable
private fun CreditCardBackground(
  backgroundColors: List<Color>,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    Spacer(
      Modifier
        .fillMaxSize()
        .background(brush = Brush.linearGradient(colors = backgroundColors)),
    )

    Text(
      text = LoremIpsum,
      color = LocalContentColor.current.copy(alpha = 0.2f),
      modifier = Modifier.padding(24.dp),
    )
  }
}

@Composable
private fun CreditCard(
  reverseIndex: Int,
  hazeState: HazeState,
  index: Int,
  shape: RoundedCornerShape,
  enabled: Boolean,
  visualEffect: VisualEffect,
  modifier: Modifier = Modifier,
  baseWidth: Float = .7f,
) {
  val perCardEffect = remember(visualEffect) {
    when (visualEffect) {
      is BlurVisualEffect -> BlurVisualEffect(visualEffect)
      is LiquidGlassVisualEffect -> LiquidGlassVisualEffect(visualEffect)
      else -> visualEffect
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth(baseWidth - (reverseIndex * 0.05f))
      .aspectRatio(16 / 9f)
      .offset { IntOffset(x = 0, y = reverseIndex * -100) }
      // We add 1 to the zIndex as the background content is zIndex 0f
      .hazeSource(hazeState, zIndex = 1f + index)
      .clip(shape)
      .then(
        if (enabled) {
          Modifier.hazeEffect(state = hazeState) {
            this.visualEffect = perCardEffect
          }
        } else {
          Modifier
        },
      ),
  ) {
    Column(Modifier.padding(32.dp)) {
      Text("Bank of Haze")
    }
  }
}

@Composable
fun OverlayingContent(
  visualEffect: VisualEffect,
  topOffset: DpOffset = DpOffset.Zero,
) {
  val hazeState = rememberHazeState()

  Box(
    modifier = Modifier
      .fillMaxSize(),
  ) {
    Spacer(
      modifier = Modifier
        .hazeSource(hazeState)
        .fillMaxSize()
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color.Red, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
          ),
        ),
    )

    Image(
      painter = painterResource(Res.drawable.photo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .hazeSource(hazeState)
        .graphicsLayer {
          scaleX = 2f
          scaleY = 2f
          rotationZ = 45f
        }
        .align(Alignment.Center)
        .size(100.dp),
    )

    val density = LocalDensity.current

    Text(
      text = "Hi",
      color = Color.White,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier
        .offset {
          with(density) {
            IntOffset(x = topOffset.x.roundToPx(), y = topOffset.y.roundToPx())
          }
        }
        .align(Alignment.Center)
        .hazeEffect(state = hazeState) {
          this.visualEffect = visualEffect
        }
        .padding(16.dp),
    )
  }
}

@Composable
fun ContentAtEdges(visualEffect: VisualEffect) {
  val hazeState = rememberHazeState()

  Box(modifier = Modifier.fillMaxSize()) {
    Image(
      painter = painterResource(Res.drawable.photo),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .hazeSource(hazeState)
        .fillMaxSize(),
    )

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxSize()
        .hazeEffect(state = hazeState) {
          this.visualEffect = visualEffect
        },
    ) {
      Text(
        text = "Content",
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

internal fun stickyHeaderBlurVisualEffect(): BlurVisualEffect {
  return BlurVisualEffect().apply {
    blurRadius = 24.dp
    colorEffects = listOf(
      HazeColorEffect.tint(
        Color.White.copy(alpha = 0.18f),
        HazeColorEffect.DefaultBlendMode,
      ),
    )
  }
}

private val StickyHeaderRows = listOf(
  listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835)),
  listOf(Color(0xFF8E24AA), Color(0xFFFFB300), Color(0xFF00ACC1), Color(0xFFD81B60)),
  listOf(Color(0xFF3949AB), Color(0xFFFF7043), Color(0xFF7CB342), Color(0xFF546E7A)),
  listOf(Color(0xFF00E5FF), Color(0xFFFF1744), Color(0xFFFFFF00), Color(0xFF00E676)),
  listOf(Color(0xFF212121), Color(0xFFFFFFFF), Color(0xFF2962FF), Color(0xFFFF6D00)),
  listOf(Color(0xFFAA00FF), Color(0xFF64DD17), Color(0xFFFFD600), Color(0xFF00B8D4)),
)

internal val LoremIpsum by lazy {
  """
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras sit amet congue mauris, iaculis accumsan eros. Aliquam pulvinar est ac elit vulputate egestas. Vestibulum consequat libero at sem varius, vitae semper urna rhoncus. Aliquam mollis, ipsum a convallis scelerisque, sem dui consequat leo, in tempor risus est ac mi. Nam vel tellus dolor. Nunc lobortis bibendum fermentum. Mauris sed mollis justo, eu tristique elit. Cras semper augue a tortor tempor, vitae vestibulum eros convallis. Curabitur id justo eget tortor iaculis lobortis. Integer pharetra augue ac elit porta iaculis non vitae libero. Nam eros turpis, suscipit at iaculis vitae, malesuada vel arcu. Donec tincidunt porttitor iaculis. Pellentesque non augue magna. Mauris mattis purus vitae mi maximus, id molestie ipsum facilisis. Donec bibendum gravida dolor nec suscipit. Pellentesque tempus felis iaculis, porta diam sed, tristique tortor.

Sed vel tellus vel augue pulvinar semper sit amet eu est. In porta arcu eu sapien luctus scelerisque. In hac habitasse platea dictumst. Aenean varius lobortis malesuada. Sed vitae ornare arcu. Nunc maximus lectus purus, vel aliquet velit facilisis a. Nulla maximus bibendum magna id vulputate. Mauris volutpat lorem et risus porta dignissim. In at elit a est vulputate tincidunt.

Nulla facilisi. Curabitur gravida quam nec massa tempus, sed placerat nunc hendrerit. Duis sit amet cursus ipsum. Phasellus eget congue lacus. Duis vehicula venenatis posuere. Morbi non tempor risus. Aenean bibendum efficitur tortor, eu interdum velit gravida rutrum. Sed tempus elementum libero. Suspendisse dapibus lorem vitae justo congue pellentesque. Phasellus et tellus sagittis, blandit nibh a, porta felis. Proin ornare eget odio eget laoreet. Cras id augue fringilla, molestie ligula sit amet, sollicitudin neque.

Suspendisse vitae bibendum justo, nec egestas mauris. Mauris id metus mi. Morbi ut maximus ex, eu consequat elit. Sed malesuada pellentesque mauris vel molestie. Nulla facilisi. Cras pellentesque metus id nibh sodales gravida. Vivamus a feugiat felis. Vivamus et justo libero. Maecenas ac augue viverra, blandit diam sed, porttitor sapien. Proin eu eros mollis, commodo lectus nec, imperdiet nisi. Proin nulla nulla, vehicula a faucibus sit amet, auctor sed lorem. Mauris ut ipsum sit amet massa posuere maximus eget porttitor nisl. Quisque nunc dolor, pharetra id nunc sit amet, maximus convallis nunc.

Ut magna diam, ullamcorper vel imperdiet at, dignissim sit amet turpis. Duis ut enim eu sapien fringilla placerat. Integer at dui eget leo tincidunt iaculis. Fusce nec elementum turpis. Aenean gravida, ipsum sit amet varius hendrerit, elit nisi hendrerit ex, et porta enim lorem eget mi. Duis convallis dolor a lacinia aliquam. Aliquam erat volutpat.
""".trim()
}

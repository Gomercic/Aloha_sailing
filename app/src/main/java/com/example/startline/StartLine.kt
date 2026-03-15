package com.example.startline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val BgBlack = Color(0xFF000000)
private val PanelBlack = Color(0xFF111111)
private val YellowText = Color(0xFFFFFF99)
private val GreenBtn = Color(0xFF4CAF50)
private val RedBtn = Color(0xFFD32F2F)
private val GreyBtn = Color(0xFF757575)
private val MidGreyText = Color(0xFFB0B0B0)
private val WhiteText = Color(0xFFFFFFFF)
private val CompactPagePadding = 6.dp
private val CompactGapXs = 4.dp
private val CompactGapS = 6.dp
private val CompactGapM = 8.dp
private val CompactGapL = 10.dp
private val CompactButtonCorner = 10.dp
private val CompactActionButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
private val CompactSmallButtonPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)

@Composable
fun StartLine(
    headerContent: (@Composable () -> Unit)? = null,
    countdownDisplayText: String = "10:00",
    isCountdownRunning: Boolean = false,
    speedDisplayText: String = "10 kn",
    leftBuoySet: Boolean = false,
    rightBuoySet: Boolean = false,
    lineLengthDisplayText: String = "254 m",
    lineEtaDisplayText: String = "00:00",
    distanceDisplayText: String = "-126 m",
    etaDeltaDisplayText: String = "-432 sec",
    statusFrameColor: Color = Color.Red,
    onDoubleClickAction: ((actionKey: String, onConfirmed: () -> Unit) -> Unit)? = null,
    onCountdownRound: () -> Unit = {},
    onCountdownStartStop: () -> Unit = {},
    onCountdownMinus: () -> Unit = {},
    onCountdownPlus: () -> Unit = {},
    onCountdownReset: () -> Unit = {},
    onSpeedMinus: () -> Unit = {},
    onSpeedFromGps: () -> Unit = {},
    onSpeedPlus: () -> Unit = {},
    onLeftBuoyToggle: () -> Unit = {},
    onRightBuoyToggle: () -> Unit = {},
    mapContent: (@Composable () -> Unit)? = null
) {
    var clockDisplayText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
    }
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        while (true) {
            clockDisplayText = formatter.format(Date())
            delay(1_000L)
        }
    }
    val view = LocalView.current
    val halfStatusBarTopPaddingPx = remember(view) {
        (ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0) / 2f
    }
    val halfStatusBarTopPadding = LocalDensity.current.run { halfStatusBarTopPaddingPx.toDp() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = halfStatusBarTopPadding)
                .padding(CompactPagePadding)
        ) {

            if (headerContent != null) {
                headerContent()
            } else {
                // Gornji red (fallback for preview)
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "StartLine",
                        color = WhiteText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(CompactGapS))
                        Text(
                            text = "REC",
                            color = WhiteText,
                            fontSize = 16.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = "11.2 kn",
                            color = YellowText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(CompactGapS))
                        Text(
                            text = "≡",
                            color = WhiteText,
                            fontSize = 52.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Timer red
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallButton(
                    text = "−",
                    onClick = {
                        onDoubleClickAction?.invoke("countdown_minus", onCountdownMinus)
                            ?: onCountdownMinus()
                    }
                )

                Spacer(modifier = Modifier.width(CompactGapM))

                Text(
                    text = countdownDisplayText,
                    color = YellowText,
                    fontSize = 90.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(onDoubleClickAction) {
                        detectTapGestures(
                            onTap = {
                                onDoubleClickAction?.invoke("countdown_round", onCountdownRound)
                                    ?: onCountdownRound()
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.width(CompactGapM))

                SmallButton(
                    text = "+",
                    onClick = {
                        onDoubleClickAction?.invoke("countdown_plus", onCountdownPlus)
                            ?: onCountdownPlus()
                    }
                )
            }

            Spacer(modifier = Modifier.height(CompactGapXs))

            // Start / Reset
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = clockDisplayText,
                    color = MidGreyText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                ActionButton(
                    text = if (isCountdownRunning) "Stop" else "Start",
                    background = GreenBtn,
                    onClick = {
                        onDoubleClickAction?.invoke("countdown_start", onCountdownStartStop)
                            ?: onCountdownStartStop()
                    }
                )

                Button(
                    onClick = {
                        onDoubleClickAction?.invoke("countdown_reset", onCountdownReset)
                            ?: onCountdownReset()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(56.dp)
                        .width(74.dp),
                    contentPadding = CompactActionButtonPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedBtn,
                        contentColor = WhiteText
                    ),
                    shape = RoundedCornerShape(CompactButtonCorner)
                ) {
                    Text(
                        text = "Reset\nStop REC",
                        fontSize = 8.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.Gray)
            )

            Spacer(modifier = Modifier.height(CompactGapM))

            // Speed row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallButton(
                    text = "−",
                    onClick = {
                        onDoubleClickAction?.invoke("speed_minus", onSpeedMinus) ?: onSpeedMinus()
                    }
                )

                Spacer(modifier = Modifier.width(CompactGapXs))

                Text(
                    text = speedDisplayText,
                    color = MidGreyText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(onDoubleClickAction) {
                        detectTapGestures(
                            onTap = {
                                onDoubleClickAction?.invoke("speed_from_gps", onSpeedFromGps)
                                    ?: onSpeedFromGps()
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.width(CompactGapXs))

                SmallButton(
                    text = "+",
                    onClick = {
                        onDoubleClickAction?.invoke("speed_plus", onSpeedPlus) ?: onSpeedPlus()
                    }
                )
            }

            Spacer(modifier = Modifier.height(CompactGapM))

            // Set L / center / Set R
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    text = if (leftBuoySet) "✓\nL" else "Set\nL",
                    background = if (leftBuoySet) GreenBtn else RedBtn,
                    modifier = Modifier
                        .width(112.dp)
                        .height(102.dp),
                    onClick = {
                        onDoubleClickAction?.invoke("left_buoy", onLeftBuoyToggle) ?: onLeftBuoyToggle()
                    }
                )

                Spacer(modifier = Modifier.width(CompactGapS))

                Column(
                    modifier = Modifier.padding(horizontal = CompactGapXs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = lineLengthDisplayText,
                        color = MidGreyText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lineEtaDisplayText,
                        color = MidGreyText,
                        fontSize = 22.sp
                    )
                }

                Spacer(modifier = Modifier.width(CompactGapS))

                ActionButton(
                    text = if (rightBuoySet) "✓\nR" else "Set\nR",
                    background = if (rightBuoySet) GreenBtn else RedBtn,
                    modifier = Modifier
                        .width(112.dp)
                        .height(102.dp),
                    onClick = {
                        onDoubleClickAction?.invoke("right_buoy", onRightBuoyToggle) ?: onRightBuoyToggle()
                    }
                )
            }

            Spacer(modifier = Modifier.height(CompactGapL))

            // Rezultat box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(4.dp, statusFrameColor, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = distanceDisplayText,
                        color = YellowText,
                        fontSize = 66.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = etaDeltaDisplayText,
                        color = YellowText,
                        fontSize = 66.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(CompactGapM))

            // Karta placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PanelBlack, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (mapContent != null) {
                    mapContent()
                } else {
                    Text(
                        text = "MAP AREA",
                        color = Color.LightGray,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CircleButton(text: String) {
    Button(
        onClick = { },
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = GreyBtn,
            contentColor = WhiteText
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SmallButton(text: String) {
    Button(
        onClick = { },
        modifier = Modifier.width(40.dp),
        contentPadding = CompactSmallButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = GreyBtn,
            contentColor = WhiteText
        ),
        shape = RoundedCornerShape(CompactButtonCorner)
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(40.dp),
        contentPadding = CompactSmallButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = GreyBtn,
            contentColor = WhiteText
        ),
        shape = RoundedCornerShape(CompactButtonCorner)
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    background: Color,
    modifier: Modifier = Modifier.height(56.dp),
    onClick: () -> Unit = {}
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = CompactActionButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = WhiteText
        ),
        shape = RoundedCornerShape(CompactButtonCorner)
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewStartLine() {
    MaterialTheme {
        StartLine()
    }
}

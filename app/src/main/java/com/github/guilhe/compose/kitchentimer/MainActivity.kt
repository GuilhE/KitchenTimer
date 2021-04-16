package com.github.guilhe.compose.kitchentimer

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.github.guilhe.compose.kitchentimer.ui.KitchenTimer
import com.github.guilhe.compose.kitchentimer.ui.TimerColors
import com.github.guilhe.compose.kitchentimer.ui.normalize
import com.github.guilhe.compose.kitchentimer.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

@ExperimentalAnimationApi
class MainActivity : AppCompatActivity() {
    private val settingPlayer: MediaPlayer by lazy { MediaPlayer.create(this, R.raw.setting) }
    private val tickPlayer: MediaPlayer by lazy { MediaPlayer.create(this, R.raw.tick) }
    private val ringPlayer: MediaPlayer by lazy { MediaPlayer.create(this, R.raw.ring) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val btnBgColor = ButtonDefaults.buttonColors(backgroundColor = if (isSystemInDarkTheme()) Color.Transparent else Color.White)
            var oneMinuteMode by rememberSaveable { mutableStateOf(true) }
            var buttonsEnabled by rememberSaveable { mutableStateOf(true) }
            var timerType by rememberSaveable { mutableStateOf(TYPE.TOMATO) }

            AppTheme {
                Surface {
                    ConstraintLayout(Modifier.fillMaxSize()) {
                        val (timer, buttons) = createRefs()
                        TimerSwitch(
                            Modifier.constrainAs(timer) { centerTo(parent) },
                            timerType,
                            onSet = {
                                buttonsEnabled = false
                                if (tickPlayer.isPlaying) {
                                    tickPlayer.stop()
                                    tickPlayer.prepare()
                                }
                                if (ringPlayer.isPlaying) {
                                    ringPlayer.stop()
                                    ringPlayer.prepare()
                                }
                                settingPlayer.start()
                            },
                            onTick = {
                                if (settingPlayer.isPlaying) {
                                    settingPlayer.stop()
                                    settingPlayer.prepare()
                                }
                                if (!tickPlayer.isPlaying) {
                                    tickPlayer.start()
                                }
                            },
                            onRing = {
                                buttonsEnabled = true
                                tickPlayer.stop()
                                if (!ringPlayer.isPlaying) {
                                    tickPlayer.prepare()
                                    ringPlayer.start()
                                    (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            it.vibrate(
                                                VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
                                            )
                                        } else {
                                            @Suppress("DEPRECATION")
                                            it.vibrate(250)
                                        }
                                    }
                                }
                            },
                            oneMinuteMode
                        )
                        AnimatedVisibility(
                            modifier = Modifier.constrainAs(buttons) {
                                bottom.linkTo(parent.bottom)
                                centerHorizontallyTo(parent)
                            }, visible = buttonsEnabled, enter = fadeIn(), exit = fadeOut()
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    Button(onClick = { timerType = TYPE.TOMATO }, colors = btnBgColor) {
                                        Text(text = stringResource(R.string.lbl_tomato), fontSize = 20.sp)
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Button(onClick = { timerType = TYPE.ORANGE }, colors = btnBgColor) {
                                        Text(text = stringResource(R.string.lbl_orange), fontSize = 20.sp)
                                    }
                                    Spacer(Modifier.size(10.dp))
                                    Button(onClick = { timerType = TYPE.LEMON }, colors = btnBgColor) {
                                        Text(text = stringResource(R.string.lbl_lemon), fontSize = 20.sp)
                                    }
                                }
                                Spacer(Modifier.size(10.dp))
                                Row(
                                    Modifier.fillMaxWidth().height(50.dp).clickable { oneMinuteMode = !oneMinuteMode },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = oneMinuteMode,
                                        onCheckedChange = { oneMinuteMode = !oneMinuteMode }
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(text = stringResource(R.string.lbl_type))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerSwitch(
    modifier: Modifier,
    timerType: TYPE,
    onSet: () -> Unit,
    onTick: () -> Unit,
    onRing: () -> Unit,
    minuteMode: Boolean
) {
    Crossfade(timerType) {
        when (it) {
            TYPE.TOMATO -> Timer(modifier, onSet, onTick, onRing, minuteMode, R.drawable.ic_tomato_leaf, PaddingValues(top = 20.dp))
            TYPE.ORANGE -> Timer(
                modifier, onSet, onTick, onRing, minuteMode, R.drawable.ic_orange_leaf, PaddingValues(start = 120.dp, top = 70.dp),
                TimerColors(Color(0xFFFFA500), Color(0xFF405F17), Color(0xFF405F17), Color(0xFF405F17), Color(0xFF405F17))
            )
            TYPE.LEMON -> Timer(
                modifier, onSet, onTick, onRing, minuteMode, R.drawable.ic_lemon_leaf, PaddingValues(start = 40.dp, top = 70.dp),
                TimerColors(Color(0xFFFFF44f), Color.Black, Color.Black, Color.Black, Color.Black)
            )
        }
    }
}

@Composable
private fun Timer(
    modifier: Modifier,
    onSet: () -> Unit,
    onTick: () -> Unit,
    onRing: () -> Unit,
    minuteMode: Boolean,
    @DrawableRes leafRes: Int,
    leafPadding: PaddingValues,
    colors: TimerColors = TimerColors()
) {
    val minute = 60 * 1000
    var currentTime by rememberSaveable { mutableStateOf(0L) }
    var deltaDragValue by rememberSaveable { mutableStateOf(0f) }
    var isSettingTime by rememberSaveable { mutableStateOf(true) }
    var newMillis by rememberSaveable { mutableStateOf(0L) }
    var enableSounds by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        ConstraintLayout(Modifier.align(Alignment.Center)
            .size(300.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        enableSounds = false
                        isSettingTime = true
                        newMillis = currentTime
                        scope.launch {
                            isSettingTime = tryAwaitRelease()
                            enableSounds = currentTime > 0 && !isSettingTime
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isSettingTime = true
                        deltaDragValue = 0f
                    },
                    onDragEnd = {
                        isSettingTime = false
                        enableSounds = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        isSettingTime = true
                        deltaDragValue -= (dragAmount * 1.4f).toInt()
                        val rightToLeft = deltaDragValue >= 0
                        val draggedTime = normalize(abs(deltaDragValue), 0f, size.width.toFloat(), 0f, minute.toFloat()).toLong()
                        val delta = when {
                            rightToLeft -> currentTime + draggedTime
                            else -> currentTime - draggedTime
                        }
                        newMillis = when {
                            delta > minute -> minute.toLong()
                            delta < 0 -> 0
                            else -> delta
                        }
                        Log.i(
                            "Timer",
                            "currentTime = $currentTime | rightToLeft = $rightToLeft | deltaDragValue = $deltaDragValue | draggedTime = $draggedTime | delta = $delta millis = $newMillis"
                        )
                        change.consumeAllChanges()
                        onSet.invoke()
                    }
                )
            }) {
            val (fruit, leaf) = createRefs()

            KitchenTimer(
                Modifier
                    .fillMaxSize()
                    .constrainAs(fruit) {
                        centerTo(parent)
                    },
                secondsInMillis = newMillis * if (minuteMode) 1 else 60,
                isSettingTime = isSettingTime,
                onTick = {
                    currentTime = it / if (minuteMode) 1 else 60
                    if (enableSounds) {
                        if (currentTime > 0) onTick.invoke() else onRing.invoke()
                    }
                },
                colors = colors,
                onMinuteMode = minuteMode
            )
            Image(
                painter = painterResource(leafRes),
                modifier = Modifier
                    .constrainAs(leaf) {
                        centerHorizontallyTo(fruit)
                        top.linkTo(fruit.top)
                        bottom.linkTo(fruit.top)
                    }
                    .scale(1.2f)
                    .padding(leafPadding),
                contentDescription = null
            )
        }
    }
}

private enum class TYPE {
    TOMATO, ORANGE, LEMON
}
package com.github.guilhe.compose.kitchentimer

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.github.guilhe.compose.kitchentimer.ui.KitchenTimer
import com.github.guilhe.compose.kitchentimer.ui.TimerColors
import com.github.guilhe.compose.kitchentimer.ui.normalize
import com.github.guilhe.compose.kitchentimer.ui.theme.AppTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingPlayer = MediaPlayer.create(this, R.raw.setting)
        val tickPlayer = MediaPlayer.create(this, R.raw.tick)
        val ringPlayer = MediaPlayer.create(this, R.raw.ring)

        setContent {
            AppTheme {
                Tomato(
                    onSet = {
                        tickPlayer.stop()
                        tickPlayer.prepare()
                        ringPlayer.stop()
                        ringPlayer.prepare()
                        settingPlayer.start()
                    },
                    onTick = {
                        settingPlayer.stop()
                        settingPlayer.prepare()
                        tickPlayer.start()
                    }
                ) {
                    tickPlayer.stop()
                    ringPlayer.start()
                    (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            it.vibrate(250)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tomato(onSet: () -> Unit, onTick: () -> Unit, onRing: () -> Unit) {
    val minute = 60 * 1000
    var currentTime by remember { mutableStateOf(0L) }
    var deltaDragValue by remember { mutableStateOf(0f) }
    var isSettingTime by remember { mutableStateOf(true) }
    var newMillis by remember { mutableStateOf(0L) }
    var enableSounds by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
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
                            enableSounds = !isSettingTime
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
            val (tomato, leaf) = createRefs()

            KitchenTimer(
                Modifier
                    .fillMaxSize()
                    .constrainAs(tomato) {
                        centerTo(parent)
                    },
                secondsInMillis = newMillis,
                isSettingTime = isSettingTime,
                onTick = {
                    currentTime = it
                    if (enableSounds) {
                        if (currentTime > 0) onTick.invoke() else onRing.invoke()
                    }
                }
            )
            Image(
                painter = painterResource(R.drawable.ic_tomato_leaf),
                modifier = Modifier
                    .constrainAs(leaf) {
                        centerHorizontallyTo(tomato)
                        top.linkTo(tomato.top)
                        bottom.linkTo(tomato.top)
                    }
                    .scale(1.2f)
                    .padding(top = 20.dp),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun Orange(onSet: () -> Unit, onTick: () -> Unit, onRing: () -> Unit) {
    val minute = 60 * 1000
    var currentTime by remember { mutableStateOf(0L) }
    var deltaDragValue by remember { mutableStateOf(0f) }
    var isSettingTime by remember { mutableStateOf(true) }
    var newMillis by remember { mutableStateOf(0L) }
    var enableSounds by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
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
            val (orange, leaf) = createRefs()

            KitchenTimer(
                Modifier
                    .fillMaxSize()
                    .constrainAs(orange) {
                        centerTo(parent)
                    },
                secondsInMillis = newMillis,
                isSettingTime = isSettingTime,
                colors = TimerColors(Color(0xFFFFA500), Color(0xFF405F17), Color(0xFF405F17), Color(0xFF405F17), Color(0xFF405F17)),
                onTick = {
                    currentTime = it
                    if (enableSounds) {
                        if (currentTime > 0) onTick.invoke() else onRing.invoke()
                    }
                }
            )

            Image(
                painter = painterResource(R.drawable.ic_orange_leaf),
                modifier = Modifier
                    .constrainAs(leaf) {
                        centerHorizontallyTo(orange)
                        top.linkTo(orange.top)
                        bottom.linkTo(orange.top)
                    }
                    .scale(1.2f)
                    .padding(start = 120.dp, top = 70.dp),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun Lemon(onSet: () -> Unit, onTick: () -> Unit, onRing: () -> Unit) {
    val minute = 60 * 1000
    var currentTime by remember { mutableStateOf(0L) }
    var deltaDragValue by remember { mutableStateOf(0f) }
    var isSettingTime by remember { mutableStateOf(true) }
    var newMillis by remember { mutableStateOf(0L) }
    var enableSounds by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
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
            val (orange, leaf) = createRefs()

            KitchenTimer(
                Modifier
                    .fillMaxSize()
                    .constrainAs(orange) {
                        centerTo(parent)
                    },
                secondsInMillis = newMillis,
                isSettingTime = isSettingTime,
                colors = TimerColors(Color(0xFFFFF44f), Color.Black, Color.Black, Color.Black, Color.Black),
                onTick = {
                    currentTime = it
                    if (enableSounds) {
                        if (currentTime > 0) onTick.invoke() else onRing.invoke()
                    }
                }
            )

            Image(
                painter = painterResource(R.drawable.ic_lemon_leaf),
                modifier = Modifier
                    .constrainAs(leaf) {
                        centerHorizontallyTo(orange)
                        top.linkTo(orange.top)
                        bottom.linkTo(orange.top)
                    }
                    .scale(1.2f)
                    .padding(start = 40.dp, top = 70.dp),
                contentDescription = null
            )
        }
    }
}

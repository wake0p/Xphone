package com.safe.discipline.ui.components

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class PickerView {
    HOUR,
    MINUTE
}

@Composable
fun PremiumTimePickerDialog(
        initialTime: String,
        title: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit
) {
    var hour by remember { mutableStateOf(initialTime.split(":")[0].toInt()) }
    var minute by remember { mutableStateOf(initialTime.split(":")[1].toInt()) }
    var currentView by remember { mutableStateOf(PickerView.HOUR) }
    val isPm = hour >= 12

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
        ) {
            Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    TimeHeaderChip(
                            value = String.format("%02d", hour),
                            isSelected = currentView == PickerView.HOUR,
                            onClick = { currentView = PickerView.HOUR }
                    )
                    Text(" : ", style = MaterialTheme.typography.displayMedium)
                    TimeHeaderChip(
                            value = String.format("%02d", minute),
                            isSelected = currentView == PickerView.MINUTE,
                            onClick = { currentView = PickerView.MINUTE }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AmPmButton(label = "AM", isSelected = !isPm) { if (isPm) hour -= 12 }
                        AmPmButton(label = "PM", isSelected = isPm) { if (!isPm) hour += 12 }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    ClockDial(
                            view = currentView,
                            selectedValue = if (currentView == PickerView.HOUR) hour else minute,
                            isPm = isPm,
                            onValueChange = { newVal ->
                                if (currentView == PickerView.HOUR) hour = newVal
                                else minute = newVal
                            },
                            onDragEnd = {
                                if (currentView == PickerView.HOUR) currentView = PickerView.MINUTE
                            }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(String.format("%02d:%02d", hour, minute)) }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
fun ClockDial(
        view: PickerView,
        selectedValue: Int,
        isPm: Boolean,
        onValueChange: (Int) -> Unit,
        onDragEnd: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    val targetAngle =
            if (view == PickerView.HOUR) {
                ((selectedValue % 12) * 30f - 90f)
            } else {
                (selectedValue * 6f - 90f)
            }

    val animatedAngle by
            animateFloatAsState(
                    targetValue = targetAngle,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                            )
            )

    BoxWithConstraints(
            modifier =
                    Modifier.fillMaxSize()
                            .pointerInput(view, isPm) {
                                detectDragGestures(
                                        onDragStart = {},
                                        onDrag = { change, _ ->
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            val touch = change.position
                                            var angle =
                                                    atan2(touch.y - center.y, touch.x - center.x) *
                                                            180 / PI.toFloat()
                                            if (angle < 0) angle += 360f

                                            val newValue =
                                                    if (view == PickerView.HOUR) {
                                                        val h =
                                                                ((angle + 90 + 15) % 360 / 30)
                                                                        .toInt()
                                                        val finalH =
                                                                if (isPm)
                                                                        (if (h == 0) 12 else h + 12)
                                                                else (if (h == 0) 0 else h)
                                                        if (finalH != selectedValue) {
                                                            vibrator.vibrate(
                                                                    VibrationEffect.createOneShot(
                                                                            10,
                                                                            VibrationEffect
                                                                                    .DEFAULT_AMPLITUDE
                                                                    )
                                                            )
                                                        }
                                                        finalH % 24
                                                    } else {
                                                        val m = ((angle + 90 + 3) % 360 / 6).toInt()
                                                        val snappedM = (m / 5) * 5
                                                        if (snappedM != selectedValue) {
                                                            vibrator.vibrate(
                                                                    VibrationEffect.createOneShot(
                                                                            15,
                                                                            VibrationEffect
                                                                                    .DEFAULT_AMPLITUDE
                                                                    )
                                                            )
                                                        }
                                                        snappedM % 60
                                                    }
                                            onValueChange(newValue)
                                        },
                                        onDragEnd = { onDragEnd() }
                                )
                            }
                            .pointerInput(view, isPm) {
                                detectTapGestures { touch ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    var angle =
                                            atan2(touch.y - center.y, touch.x - center.x) * 180 /
                                                    PI.toFloat()
                                    if (angle < 0) angle += 360f
                                    val newValue =
                                            if (view == PickerView.HOUR) {
                                                val h = ((angle + 90 + 15) % 360 / 30).toInt()
                                                (if (isPm) (if (h == 0) 12 else h + 12)
                                                else (if (h == 0) 0 else h)) % 24
                                            } else {
                                                ((angle + 90 + 3) % 360 / 6).toInt()
                                            }
                                    onValueChange(newValue)
                                    onDragEnd()
                                }
                            }
    ) {
        val radius = minHeight / 2
        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                    color = onSurfaceColor.copy(alpha = 0.05f),
                    radius = radius.toPx(),
                    center = center
            )

            val textPaint =
                    android.graphics.Paint().apply {
                        color = onSurfaceColor.toArgb()
                        textSize = 16.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

            val itemRadius = radius.toPx() * 0.8f
            for (i in 0..11) {
                val angle = (i * 30 - 90) * PI / 180
                val x = center.x + cos(angle).toFloat() * itemRadius
                val y = center.y + sin(angle).toFloat() * itemRadius
                val digit =
                        if (view == PickerView.HOUR) {
                            if (isPm) (if (i == 0) "12" else (i + 12).toString())
                            else (if (i == 0) "0" else i.toString())
                        } else (i * 5).toString()
                drawContext.canvas.nativeCanvas.drawText(digit, x, y + 6.dp.toPx(), textPaint)
            }

            val handAngleRad = animatedAngle * PI / 180
            val handEnd =
                    Offset(
                            center.x + cos(handAngleRad).toFloat() * itemRadius,
                            center.y + sin(handAngleRad).toFloat() * itemRadius
                    )
            drawLine(
                    color = primaryColor,
                    start = center,
                    end = handEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
            )
            drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = center)
            drawCircle(color = primaryColor, radius = 20.dp.toPx(), center = handEnd)
            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = handEnd)
        }
    }
}

@Composable
fun TimeHeaderChip(value: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp, 80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                    value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color =
                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AmPmButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
            modifier = Modifier.size(48.dp, 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                            if (isSelected) MaterialTheme.colorScheme.onSecondary
                            else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ReadOnlyTextField(
        value: String,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedTextField(
                value = value,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    Icon(Icons.Filled.AccessTime, null, tint = MaterialTheme.colorScheme.primary)
                }
        )
        Box(
                modifier =
                        Modifier.matchParentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onClick)
        )
    }
}

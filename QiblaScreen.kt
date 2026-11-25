package com.example.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.NoorPreferences
import com.example.domain.QiblaCalculator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QiblaScreen(
    prefs: NoorPreferences,
    onOpenLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val qiblaBearing = remember(prefs.city.latitude, prefs.city.longitude) {
        QiblaCalculator.calculateBearing(prefs.city.latitude, prefs.city.longitude)
    }
    val distanceKm = remember(prefs.city.latitude, prefs.city.longitude) {
        QiblaCalculator.distanceToMakkahKm(prefs.city.latitude, prefs.city.longitude)
    }
    val declination = remember(prefs.city.latitude, prefs.city.longitude) {
        QiblaCalculator.magneticDeclination(prefs.city.latitude, prefs.city.longitude)
    }

    var hasSensor by remember { mutableStateOf(true) }
    var rawAzimuthDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context, declination) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (sensorManager == null || (rotSensor == null && (accelSensor == null || magSensor == null))) {
            hasSensor = false
            onDispose { }
        } else {
            hasSensor = true
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var hasGravity = false
            var hasMag = false
            var smoothedAzimuth = 0f
            var initialized = false

            fun pushAzimuth(magneticDeg: Float) {
                val trueNorthDeg = ((magneticDeg + declination) % 360f + 360f) % 360f
                if (!initialized) {
                    smoothedAzimuth = trueNorthDeg
                    initialized = true
                } else {
                    var delta = trueNorthDeg - smoothedAzimuth
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    smoothedAzimuth = ((smoothedAzimuth + delta * 0.18f) % 360f + 360f) % 360f
                }
                rawAzimuthDeg = smoothedAzimuth
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                            pushAzimuth(deg)
                        }

                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, gravity, 0, 3)
                            hasGravity = true
                            if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                                SensorManager.getOrientation(rotationMatrix, orientation)
                                pushAzimuth(Math.toDegrees(orientation[0].toDouble()).toFloat())
                            }
                        }

                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                            hasMag = true
                            if (hasGravity && SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                                SensorManager.getOrientation(rotationMatrix, orientation)
                                pushAzimuth(Math.toDegrees(orientation[0].toDouble()).toFloat())
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            if (rotSensor != null) {
                sensorManager.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val effectiveAzimuth = if (hasSensor) rawAzimuthDeg else 0f
    val dialRotation by animateFloatAsState(
        targetValue = -effectiveAzimuth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "compass_dial_rotation"
    )

    val relativeAngle = ((qiblaBearing.toFloat() - effectiveAzimuth) % 360f + 360f) % 360f
    val shortestDelta = if (relativeAngle > 180f) 360f - relativeAngle else relativeAngle
    val isAligned = hasSensor && shortestDelta <= 4f

    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(isAligned) {
        if (isAligned && !wasAligned && prefs.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasAligned = isAligned
    }

    val alignmentGlowColor by animateColorAsState(
        targetValue = if (isAligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        label = "qibla_glow"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val goldColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isAligned) MaterialTheme.colorScheme.primaryContainer else containerColor,
                border = BorderStroke(
                    1.dp,
                    if (isAligned) primaryColor else outlineColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qibla_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasSensor) Icons.Outlined.Explore else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = if (isAligned) primaryColor else goldColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isAligned -> "Aligned with Qibla • Facing the Holy Kaaba"
                            hasSensor -> "Hold phone flat and rotate toward the golden Kaaba needle"
                            else -> "Magnetometer unavailable — showing North-up Qibla bearing (${String.format(Locale.US, "%.1f°", qiblaBearing)})"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAligned) MaterialTheme.colorScheme.onPrimaryContainer else onSurfaceColor
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
                    .testTag("qibla_compass_dial"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerRadius = size.minDimension / 2f - 10.dp.toPx()

                    // Outer bezel
                    drawCircle(
                        color = surfaceColor,
                        radius = outerRadius,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = if (isAligned) primaryColor else outlineColor,
                        radius = outerRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = if (isAligned) 3.dp.toPx() else 1.5.dp.toPx())
                    )

                    // Inner recessed ring
                    drawCircle(
                        color = containerColor,
                        radius = outerRadius * 0.80f,
                        center = Offset(cx, cy)
                    )

                    // Rotating compass rose (True North aligned)
                    rotate(degrees = dialRotation, pivot = Offset(cx, cy)) {
                        for (deg in 0 until 360 step 5) {
                            val isMajor = deg % 30 == 0
                            val isCardinal = deg % 90 == 0
                            val rad = (deg - 90) * PI / 180.0
                            val tickOuter = outerRadius * 0.94f
                            val tickInner = when {
                                isCardinal -> outerRadius * 0.82f
                                isMajor -> outerRadius * 0.86f
                                else -> outerRadius * 0.90f
                            }
                            val start = Offset(
                                cx + (tickInner * cos(rad)).toFloat(),
                                cy + (tickInner * sin(rad)).toFloat()
                            )
                            val end = Offset(
                                cx + (tickOuter * cos(rad)).toFloat(),
                                cy + (tickOuter * sin(rad)).toFloat()
                            )
                            drawLine(
                                color = when {
                                    deg == 0 -> Color(0xFFD64545)
                                    isCardinal -> primaryColor
                                    isMajor -> onSurfaceColor.copy(alpha = 0.6f)
                                    else -> mutedColor.copy(alpha = 0.35f)
                                },
                                start = start,
                                end = end,
                                strokeWidth = if (isCardinal) 2.5.dp.toPx() else 1.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Qibla Needle attached at true bearing relative to North
                        rotate(degrees = qiblaBearing.toFloat(), pivot = Offset(cx, cy)) {
                            val needleTipY = cy - outerRadius * 0.72f
                            val needleTailY = cy + outerRadius * 0.25f

                            val needlePath = Path().apply {
                                moveTo(cx, needleTipY)
                                lineTo(cx - 11.dp.toPx(), cy)
                                lineTo(cx, needleTailY)
                                lineTo(cx + 11.dp.toPx(), cy)
                                close()
                            }
                            drawPath(
                                path = needlePath,
                                color = alignmentGlowColor
                            )

                            // Kaaba symbolic gold square near tip
                            val boxSize = 18.dp.toPx()
                            drawRect(
                                color = Color(0xFF141414),
                                topLeft = Offset(cx - boxSize / 2f, needleTipY - boxSize * 0.55f),
                                size = Size(boxSize, boxSize)
                            )
                            drawRect(
                                color = Color(0xFFE8C56E),
                                topLeft = Offset(cx - boxSize / 2f, needleTipY - boxSize * 0.30f),
                                size = Size(boxSize, 3.dp.toPx())
                            )
                        }
                    }

                    // Top device heading marker
                    drawLine(
                        color = primaryColor,
                        start = Offset(cx, cy - outerRadius - 6.dp.toPx()),
                        end = Offset(cx, cy - outerRadius + 8.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Center hub
                Surface(
                    shape = CircleShape,
                    color = if (isAligned) primaryColor else surfaceColor,
                    border = BorderStroke(2.dp, alignmentGlowColor),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(66.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Mosque,
                            contentDescription = "Kaaba direction",
                            tint = if (isAligned) MaterialTheme.colorScheme.onPrimary else alignmentGlowColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        item {
            val cardinal = QiblaCalculator.cardinalDirectionLabel(qiblaBearing)
            val distVal = if (prefs.distanceInMiles) distanceKm * 0.621371 else distanceKm
            val distUnit = if (prefs.distanceInMiles) "mi" else "km"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QiblaMetricCard(
                    label = "QIBLA BEARING",
                    value = String.format(Locale.US, "%.1f° %s", qiblaBearing, cardinal),
                    subtitle = "From True North",
                    modifier = Modifier.weight(1f)
                )
                QiblaMetricCard(
                    label = "DISTANCE TO MAKKAH",
                    value = String.format(Locale.US, "%,.0f %s", distVal, distUnit),
                    subtitle = "Great-circle route",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = surfaceColor,
                border = BorderStroke(1.dp, outlineColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenLocationPicker)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = primaryColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${prefs.city.name}, ${prefs.city.country}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Lat %.4f°, Lon %.4f° • Declination %+.1f°",
                                    prefs.city.latitude,
                                    prefs.city.longitude,
                                    declination
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedColor
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QiblaMetricCard(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

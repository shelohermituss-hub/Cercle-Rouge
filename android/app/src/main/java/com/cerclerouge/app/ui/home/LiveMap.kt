package com.cerclerouge.app.ui.home

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cerclerouge.app.ui.theme.Cr
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Centre par défaut quand aucune position n'est lue : Port-au-Prince. */
private val DEFAULT_CENTER = LatLng(18.5425, -72.3386)

private suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont -> getMapAsync { cont.resume(it) } }

/**
 * Carte réelle de l'accueil (MapLibre + tuiles OpenFreeMap, ADR-0018).
 * - `armed` : style en couleurs, sinon noir et blanc.
 * - `showMe` : point de position en direct, lu en local (premier plan seulement). Faux → GPS jamais sollicité (P4).
 * Aucune position n'est envoyée : seules les tuiles de la zone affichée sont téléchargées.
 */
@SuppressLint("MissingPermission") // showMe n'est vrai que si ACCESS_FINE_LOCATION est accordée (EngineState.locationGranted).
@Composable
fun LiveMap(armed: Boolean, showMe: Boolean, modifier: Modifier = Modifier, coveredBottom: Dp = 340.dp, coveredTop: Dp = 40.dp) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val c = Cr.colors
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView({ mapView }, modifier)

    LaunchedEffect(armed, showMe) {
        val map = mapView.awaitMap()
        map.uiSettings.apply {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = false // attribution OpenStreetMap affichée en texte sur l'accueil
            isCompassEnabled = false
        }
        // Le losange couvre le bas de la carte : le centre (et donc ton point) est placé dans la partie visible, au-dessus.
        with(density) { map.setPadding(0, coveredTop.roundToPx(), 0, coveredBottom.roundToPx()) }
        map.setStyle(Style.Builder().fromJson(MapStyle.json(armed))) { style ->
            val location = map.locationComponent
            if (showMe) {
                val puck = LocationComponentOptions.builder(context)
                    .foregroundTintColor(c.lime.toArgb())
                    .backgroundTintColor(c.surface.toArgb())
                    .accuracyColor(c.lime.toArgb())
                    .accuracyAlpha(0.12f)
                    .pulseEnabled(true)
                    .pulseColor(c.lime.toArgb())
                    .build()
                location.activateLocationComponent(
                    LocationComponentActivationOptions.builder(context, style)
                        .locationComponentOptions(puck)
                        .useDefaultLocationEngine(true)
                        .build()
                )
                location.isLocationComponentEnabled = true
                location.renderMode = RenderMode.NORMAL
                location.cameraMode = CameraMode.TRACKING
                location.zoomWhileTracking(15.0)
            } else {
                if (location.isLocationComponentActivated) location.isLocationComponentEnabled = false
                map.cameraPosition = CameraPosition.Builder().target(DEFAULT_CENTER).zoom(12.5).build()
            }
        }
    }
}

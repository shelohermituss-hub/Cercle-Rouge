package com.cerclerouge.app.ui.home

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.cerclerouge.location.GeoPoint
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

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
fun LiveMap(
    armed: Boolean,
    showMe: Boolean,
    modifier: Modifier = Modifier,
    coveredBottom: Dp = 240.dp,
    coveredTop: Dp = 40.dp,
    coveredEnd: Dp = 72.dp,
    destination: GeoPoint? = null,
    onTap: ((GeoPoint) -> Unit)? = null
) {
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

    val destStyle = remember { mutableStateOf<Style?>(null) }
    val tapHandler by rememberUpdatedState(onTap)
    LaunchedEffect(armed, showMe) {
        val map = mapView.awaitMap()
        map.uiSettings.apply {
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = false // attribution OpenStreetMap affichée en texte sur l'accueil
            isCompassEnabled = false
        }
        // Le losange (en bas) et les boutons (à droite) couvrent la carte : le centre (et donc ton point) reste dans la partie libre.
        with(density) { map.setPadding(0, coveredTop.roundToPx(), coveredEnd.roundToPx(), coveredBottom.roundToPx()) }
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
            // Repère de destination (écran Trajet) : un point lime cerclé de noir, mis à jour sans recharger le style.
            style.addSource(GeoJsonSource(DEST_SOURCE))
            style.addLayer(
                CircleLayer(DEST_LAYER, DEST_SOURCE).withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor(c.lime.toArgb()),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor(c.void.toArgb())
                )
            )
            destStyle.value = style
        }
    }
    LaunchedEffect(destination, destStyle.value) {
        val style = destStyle.value ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(DEST_SOURCE) ?: return@LaunchedEffect
        if (destination == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(Point.fromLngLat(destination.lon, destination.lat))
            if (!showMe) {
                mapView.awaitMap().cameraPosition =
                    CameraPosition.Builder().target(LatLng(destination.lat, destination.lon)).zoom(14.0).build()
            }
        }
    }
    LaunchedEffect(Unit) {
        mapView.awaitMap().addOnMapClickListener { latLng ->
            val tap = tapHandler ?: return@addOnMapClickListener false
            tap(GeoPoint(latLng.latitude, latLng.longitude))
            true
        }
    }
}

private const val DEST_SOURCE = "cr-destination"
private const val DEST_LAYER = "cr-destination-dot"

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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Centre par défaut quand aucune position n'est lue : Port-au-Prince. */
private val DEFAULT_CENTER = LatLng(18.5425, -72.3386)

/** Zoom de départ (quartiers lisibles) ; jamais plus loin que MIN_ZOOM : la carte ne montre plus le monde entier. */
private const val START_ZOOM = 13.0
private const val FOLLOW_ZOOM = 15.0
private const val MIN_ZOOM = 6.0
private const val FIT_PADDING_DP = 56

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
    origin: GeoPoint? = null,
    route: List<GeoPoint> = emptyList(),
    routeIsRoad: Boolean = true,
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
    val centered = remember { mutableStateOf(false) }
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
        map.setMinZoomPreference(MIN_ZOOM)
        // Vue de départ immédiate (avant le style et avant tout point GPS) : sinon la carte s'ouvre sur le monde entier.
        if (!centered.value) {
            val start = origin?.let { LatLng(it.lat, it.lon) } ?: DEFAULT_CENTER
            map.cameraPosition = CameraPosition.Builder().target(start).zoom(START_ZOOM).build()
        }
        // Les boutons (à droite) et le fondu (en bas) couvrent la carte : le centre (et donc ton point) reste dans la partie libre.
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
                // Dernière position connue : la carte s'ouvre tout de suite sur toi, sans attendre le premier point GPS.
                location.lastKnownLocation?.let {
                    map.cameraPosition = CameraPosition.Builder().target(LatLng(it.latitude, it.longitude)).zoom(FOLLOW_ZOOM).build()
                    centered.value = true
                }
                location.cameraMode = CameraMode.TRACKING
                location.zoomWhileTracking(FOLLOW_ZOOM)
            } else {
                if (location.isLocationComponentActivated) location.isLocationComponentEnabled = false
            }
            // Route à suivre : liseré sombre puis trait ambre (couleur du trajet), sous le repère de destination.
            style.addSource(GeoJsonSource(ROUTE_SOURCE))
            style.addLayer(
                LineLayer(ROUTE_CASING, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor(c.void.toArgb()),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round")
                )
            )
            style.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor(c.warn.toArgb()),
                    PropertyFactory.lineWidth(5f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round")
                )
            )
            // Repère de destination (écran Trajet) : un point lime cerclé de noir, mis à jour sans recharger le style.
            style.addSource(GeoJsonSource(DEST_SOURCE))
            style.addLayer(
                CircleLayer(DEST_LAYER, DEST_SOURCE).withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor(c.warn.toArgb()),
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
        }
    }
    // Départ connu après coup (écran Trajet) : la carte se recentre une fois sur toi, tant qu'aucune destination n'est choisie.
    LaunchedEffect(origin) {
        val o = origin ?: return@LaunchedEffect
        if (showMe || destination != null || centered.value) return@LaunchedEffect
        centered.value = true
        mapView.awaitMap().cameraPosition = CameraPosition.Builder().target(LatLng(o.lat, o.lon)).zoom(START_ZOOM + 1).build()
    }
    // Route : tracée dès qu'elle change (trait plein par les rues, pointillés pour la ligne droite) ; la carte cadre tout le trajet.
    LaunchedEffect(route, routeIsRoad, destStyle.value) {
        val style = destStyle.value ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return@LaunchedEffect
        if (route.size < 2) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }
        source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) })))
        val dash = if (routeIsRoad) arrayOf(1f, 0f) else arrayOf(1.5f, 1.5f)
        style.getLayerAs<LineLayer>(ROUTE_LAYER)?.setProperties(PropertyFactory.lineDasharray(dash))
        if (!showMe && route.distinct().size >= 2) {
            val bounds = LatLngBounds.Builder().apply { route.forEach { include(LatLng(it.lat, it.lon)) } }.build()
            val pad = with(density) { FIT_PADDING_DP.dp.roundToPx() }
            centered.value = true
            mapView.awaitMap().easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad), 400)
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

private const val ROUTE_SOURCE = "cr-route"
private const val ROUTE_CASING = "cr-route-casing"
private const val ROUTE_LAYER = "cr-route-line"
private const val DEST_SOURCE = "cr-destination"
private const val DEST_LAYER = "cr-destination-dot"

package com.ndregil.inchat.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationBridge(private val onLocationChanged: (Double, Double) -> Unit) {
    @JavascriptInterface
    fun onLocationChanged(lat: Double, lng: Double) {
        onLocationChanged(lat, lng)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveMapPickerDialog(
    onDismiss: () -> Unit,
    onShareLocation: (latitude: Double, longitude: Double, label: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedLat by remember { mutableDoubleStateOf(-6.200000) }
    var selectedLng by remember { mutableDoubleStateOf(106.816666) }
    var locationName by remember { mutableStateOf("Jakarta Pusat") }
    var locationAddress by remember { mutableStateOf("DKI Jakarta, Indonesia") }
    var isGeocoding by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isRendererCrashed by remember { mutableStateOf(false) }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Safe lifecycle cleanup on dismissal
    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewRef.value?.let { wv ->
                    wv.stopLoading()
                    wv.webViewClient = WebViewClient()
                    wv.loadUrl("about:blank")
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
                webViewRef.value = null
            } catch (_: Exception) {}
        }
    }

    // GPS location fetcher helper
    fun fetchGpsPosition() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            Toast.makeText(context, "Layanan lokasi tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetEnabled) {
            Toast.makeText(context, "Silakan aktifkan GPS / Lokasi di HP Anda", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val lastGps = if (isGpsEnabled) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val lastNet = if (isNetEnabled) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            val best = lastGps ?: lastNet

            if (best != null) {
                val lat = best.latitude
                val lng = best.longitude
                selectedLat = lat
                selectedLng = lng
                webViewRef.value?.evaluateJavascript("setCenter($lat, $lng, 17)", null)
                Toast.makeText(context, "Lokasi GPS terkini ditemukan", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Mendapatkan sinyal GPS...", Toast.LENGTH_SHORT).show()
                val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val lat = location.latitude
                        val lng = location.longitude
                        selectedLat = lat
                        selectedLng = lng
                        webViewRef.value?.evaluateJavascript("setCenter($lat, $lng, 17)", null)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, null)
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Izin lokasi diperlukan", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membaca GPS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchGpsPosition()
        } else {
            Toast.makeText(context, "Izin lokasi ditolak. Anda tetap bisa menggeser peta secara manual.", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndFetchGps() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fetchGpsPosition()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Attempt GPS detection on start if permission already granted
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchGpsPosition()
        }
    }

    // Reverse Geocoding when coordinates change (map panned)
    LaunchedEffect(selectedLat, selectedLng) {
        isGeocoding = true
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = geocoder.getFromLocation(selectedLat, selectedLng, 1)
                if (!results.isNullOrEmpty()) {
                    val addr = results[0]
                    val feature = addr.featureName
                    val street = addr.thoroughfare ?: addr.subThoroughfare
                    val subLoc = addr.subLocality ?: addr.locality
                    val admin = addr.subAdminArea ?: addr.adminArea
                    val line = addr.getAddressLine(0)

                    val primaryName = listOfNotNull(feature, street, subLoc).firstOrNull { !it.isNullOrBlank() }
                        ?: admin ?: "Lokasi Terpilih"

                    val secondaryAddr = line ?: "$primaryName, $admin"

                    withContext(Dispatchers.Main) {
                        locationName = primaryName
                        locationAddress = secondaryAddr
                        isGeocoding = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        locationName = "Lokasi Terpilih"
                        locationAddress = "Koordinat: %.6f, %.6f".format(Locale.US, selectedLat, selectedLng)
                        isGeocoding = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    locationName = "Lokasi Terpilih"
                    locationAddress = "Koordinat: %.6f, %.6f".format(Locale.US, selectedLat, selectedLng)
                    isGeocoding = false
                }
            }
        }
    }

    // Search Place function
    fun searchPlace(query: String) {
        if (query.isBlank()) return
        isSearching = true
        scope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = geocoder.getFromLocationName(query, 1)
                if (!results.isNullOrEmpty()) {
                    val found = results[0]
                    val lat = found.latitude
                    val lng = found.longitude
                    withContext(Dispatchers.Main) {
                        selectedLat = lat
                        selectedLng = lng
                        webViewRef.value?.evaluateJavascript("setCenter($lat, $lng, 16)", null)
                        isSearching = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tempat tidak ditemukan", Toast.LENGTH_SHORT).show()
                        isSearching = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal mencari alamat", Toast.LENGTH_SHORT).show()
                    isSearching = false
                }
            }
        }
    }

    val htmlContent = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                * { box-sizing: border-box; }
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #f1f5f9;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    -webkit-user-select: none;
                    user-select: none;
                }
                #map {
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    width: 100%;
                    height: 100%;
                    z-index: 1;
                }
                .center-pin {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    width: 42px;
                    height: 42px;
                    margin-top: -42px;
                    margin-left: -21px;
                    z-index: 1000;
                    pointer-events: none;
                    transition: transform 0.15s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                }
                .pin-shadow {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    width: 16px;
                    height: 8px;
                    margin-top: -4px;
                    margin-left: -8px;
                    background: rgba(0,0,0,0.35);
                    border-radius: 50%;
                    z-index: 999;
                    pointer-events: none;
                    transition: transform 0.15s ease-out;
                }
                .leaflet-control-attribution { display: none !important; }
                .leaflet-bar { border-radius: 8px !important; overflow: hidden; box-shadow: 0 2px 6px rgba(0,0,0,0.2) !important; border: none !important; }
                .leaflet-bar a { background-color: #ffffff !important; color: #1e293b !important; border-bottom: 1px solid #e2e8f0 !important; width: 34px !important; height: 34px !important; line-height: 34px !important; font-size: 16px !important; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <div class="pin-shadow" id="pinShadow"></div>
            <svg class="center-pin" id="centerPin" viewBox="0 0 24 24" fill="#EF4444">
                <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
            </svg>

            <script>
                var initialLat = -6.200000;
                var initialLng = 106.816666;

                var map = L.map('map', {
                    center: [initialLat, initialLng],
                    zoom: 16,
                    zoomControl: false,
                    dragging: true,
                    tap: false,
                    touchZoom: true
                });

                // Add zoom control at top-right
                L.control.zoom({ position: 'topright' }).addTo(map);

                // Google Maps Road layer
                var googleRoadLayer = L.tileLayer('https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                    maxZoom: 20,
                    subdomains: ['0', '1', '2', '3']
                });

                // CartoDB Voyager as fallback
                var cartoLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19,
                    subdomains: 'abcd'
                });

                googleRoadLayer.addTo(map);

                googleRoadLayer.on('tileerror', function() {
                    if (!map.hasLayer(cartoLayer)) {
                        cartoLayer.addTo(map);
                    }
                });

                function updateLocation() {
                    var center = map.getCenter();
                    if (window.AndroidLocationPicker) {
                        window.AndroidLocationPicker.onLocationChanged(center.lat, center.lng);
                    }
                }

                map.on('movestart', function() {
                    document.getElementById('centerPin').style.transform = 'translateY(-14px) scale(1.15)';
                    document.getElementById('pinShadow').style.transform = 'scale(0.7)';
                });

                map.on('moveend', function() {
                    document.getElementById('centerPin').style.transform = 'translateY(0px) scale(1.0)';
                    document.getElementById('pinShadow').style.transform = 'scale(1.0)';
                    updateLocation();
                });

                function setCenter(lat, lng, zoom) {
                    if (map) {
                        map.setView([lat, lng], zoom || 16, { animate: true });
                        setTimeout(function() { map.invalidateSize(); }, 100);
                    }
                }

                // Ensure Leaflet calculates dimensions correctly
                setTimeout(function() { if (map) map.invalidateSize(); }, 250);
                setTimeout(function() { if (map) map.invalidateSize(); }, 600);
                setTimeout(function() { if (map) map.invalidateSize(); }, 1200);
                window.addEventListener('resize', function() { if (map) map.invalidateSize(); });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header & Search Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Kirim Lokasi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Geser peta untuk menentukan titik lokasi presisi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search Input Field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari nama jalan atau kota...", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Bersihkan", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { searchPlace(searchQuery) }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_search_location"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = { searchPlace(searchQuery) },
                                enabled = searchQuery.isNotBlank() && !isSearching,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Cari", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Interactive Map Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isRendererCrashed) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Peta perlu dimuat ulang", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { isRendererCrashed = false }) {
                                    Text("Muat Ulang Peta")
                                }
                            }
                        }
                    } else {
                        @SuppressLint("SetJavaScriptEnabled")
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    // Use Software layer to prevent GPU crashes on emulators/virtual devices
                                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 InChat/1.0"

                                    isClickable = true
                                    isFocusable = true
                                    isFocusableInTouchMode = true

                                    // Disallow parent Compose dialog/scaffold from intercepting drag touch events
                                    setOnTouchListener { v, event ->
                                        when (event.action) {
                                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                            }
                                        }
                                        false
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                            try {
                                                (view?.parent as? ViewGroup)?.removeView(view)
                                                view?.destroy()
                                            } catch (_: Exception) {}
                                            webViewRef.value = null
                                            isRendererCrashed = true
                                            return true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            view?.evaluateJavascript("setCenter($selectedLat, $selectedLng, 16);", null)
                                        }
                                    }

                                    addJavascriptInterface(LocationBridge { lat, lng ->
                                        scope.launch(Dispatchers.Main) {
                                            selectedLat = lat
                                            selectedLng = lng
                                        }
                                    }, "AndroidLocationPicker")

                                    loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
                                    webViewRef.value = this
                                }
                            },
                            onRelease = { wv ->
                                try {
                                    wv.stopLoading()
                                    wv.webViewClient = WebViewClient()
                                    wv.loadUrl("about:blank")
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                    wv.destroy()
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating Action Button: Snap to Real Device GPS Location
                    SmallFloatingActionButton(
                        onClick = { checkAndFetchGps() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFFEF4444),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("btn_gps_my_location")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Lokasi Saya (GPS)")
                    }
                }

                // Bottom Selected Location Action Card (WhatsApp style)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = locationName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isGeocoding) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    }
                                }

                                Text(
                                    text = locationAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.5.sp
                                )

                                Text(
                                    text = "Koordinat: %.6f, %.6f".format(Locale.US, selectedLat, selectedLng),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        // Send Selected Location Button
                        Button(
                            onClick = {
                                onShareLocation(selectedLat, selectedLng, locationName)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_send_selected_location"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kirim Lokasi Terpilih Ini", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Send Live Current GPS Location Button
                        OutlinedButton(
                            onClick = {
                                checkAndFetchGps()
                                onShareLocation(selectedLat, selectedLng, "Lokasi GPS Terkini")
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kirim Lokasi GPS Terkini", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

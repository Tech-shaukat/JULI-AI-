package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateSuccess
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapsPanel(
    currentDestination: String,
    currentTravelMode: String,
    onDestinationChange: (String) -> Unit,
    onTravelModeChange: (String) -> Unit,
    onStartNavigation: (String, String) -> Unit,
    onSearchPlace: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var destinationInput by remember(currentDestination) { mutableStateOf(currentDestination) }
    val hasMapsApiKey = remember {
        val k = BuildConfig.MAPS_API_KEY.trim()
        k.isNotEmpty() && !k.startsWith("YOUR_")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Google Maps Navigation (मैप एवं रास्ता)",
            color = CyberCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = destinationInput,
                    onValueChange = {
                        destinationInput = it
                        onDestinationChange(it)
                    },
                    label = { Text("गंतव्य (Destination / Address)", fontSize = 12.sp) },
                    placeholder = { Text("उदा. Patna Junction, India Gate...", color = TextDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCardElevated,
                        unfocusedContainerColor = SurfaceCardElevated,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_destination_input")
                )

                val modes = listOf(
                    Triple("driving", "Car", Icons.Default.DirectionsCar),
                    Triple("two_wheeler", "Bike", Icons.Default.TwoWheeler),
                    Triple("walking", "Walk", Icons.AutoMirrored.Filled.DirectionsWalk),
                    Triple("bicycling", "Cycle", Icons.AutoMirrored.Filled.DirectionsBike)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.forEach { (modeKey, label, icon) ->
                        val selected = currentTravelMode == modeKey
                        FilterChip(
                            selected = selected,
                            onClick = { onTravelModeChange(modeKey) },
                            label = { Text(label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan,
                                selectedLeadingIconColor = CyberCyan,
                                containerColor = SurfaceCardElevated,
                                labelColor = TextSecondary,
                                iconColor = TextSecondary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("map_mode_$modeKey")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onStartNavigation(destinationInput, currentTravelMode) },
                        enabled = destinationInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            disabledContainerColor = SurfaceBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("start_map_navigation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Start Navigation",
                            tint = DeepSpace,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Route", color = DeepSpace, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onSearchPlace(destinationInput) },
                        enabled = destinationInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("search_map_place_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Place",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Find", color = CyberCyan)
                    }
                }

                Text(
                    text = "Quick Places • तुरंत खोजें:",
                    color = TextDim,
                    fontSize = 11.sp
                )

                val quickPlaces = listOf(
                    "Nearest Hospital",
                    "Railway Station",
                    "Petrol Pump",
                    "ATM near me",
                    "Airport"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPlaces.forEach { place ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                destinationInput = place
                                onDestinationChange(place)
                                onStartNavigation(place, currentTravelMode)
                            },
                            label = { Text(place, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceCardElevated,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("quick_place_${place.lowercase().replace(' ', '_')}")
                        )
                    }
                }
            }
        }

        // Embedded Google Map Compose view when MAPS_API_KEY is configured
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (hasMapsApiKey) {
                val indiaCenter = LatLng(25.5941, 85.1376) // Patna default center
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(indiaCenter, 11f)
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Embedded Map",
                            tint = StateSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (destinationInput.isNotBlank()) {
                                "Map Preview • Target: $destinationInput"
                            } else {
                                "Interactive Map Preview"
                            },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState
                        ) {
                            Marker(
                                state = MarkerState(position = indiaCenter),
                                title = destinationInput.ifBlank { "Selected Location" }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Google Maps",
                            tint = NeonPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voice & Turn-by-Turn Maps Ready",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• बोलकर कहें: \"Patna Junction का मैप सेट कर दो\" या ऊपर से \"Start Route\" दबाएँ — सीधे Google Maps नेविगेशन शुरू हो जाएगा।\n• ऐप के अंदर लाइव मैप प्रिव्यू दिखाने के लिए AI Studio Secrets में MAPS_API_KEY जोड़ें।",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

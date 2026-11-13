package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.theme.CosmicMagenta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StateError
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ImageStudioUiState
import com.example.viewmodel.VideoStudioUiState
import java.io.File

@Composable
fun CreativeStudioPanel(
    imageState: ImageStudioUiState,
    videoState: VideoStudioUiState,
    hasVideoApiKey: Boolean,
    onGenerateImage: (String, String) -> Unit,
    onEditImage: (Bitmap, String) -> Unit,
    onGenerateVideo: (String, String, Boolean) -> Unit,
    onShareMedia: (File, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudioTab by remember { mutableStateOf("image") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "AI Image & Video Studio",
            color = CyberCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = selectedStudioTab == "image",
                onClick = { selectedStudioTab = "image" },
                label = { Text("Image Studio", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image Studio",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                    selectedLabelColor = CyberCyan,
                    selectedLeadingIconColor = CyberCyan,
                    containerColor = SurfaceCard,
                    labelColor = TextSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("studio_tab_image")
            )

            FilterChip(
                selected = selectedStudioTab == "video",
                onClick = { selectedStudioTab = "video" },
                label = { Text("Video Studio", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = "Video Studio",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CosmicMagenta.copy(alpha = 0.2f),
                    selectedLabelColor = CosmicMagenta,
                    selectedLeadingIconColor = CosmicMagenta,
                    containerColor = SurfaceCard,
                    labelColor = TextSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("studio_tab_video")
            )
        }

        if (selectedStudioTab == "image") {
            ImageStudioSection(
                imageState = imageState,
                onGenerateImage = onGenerateImage,
                onEditImage = onEditImage,
                onShareMedia = onShareMedia
            )
        } else {
            VideoStudioSection(
                videoState = videoState,
                hasVideoApiKey = hasVideoApiKey,
                onGenerateVideo = onGenerateVideo,
                onShareMedia = onShareMedia
            )
        }
    }
}

@Composable
private fun ImageStudioSection(
    imageState: ImageStudioUiState,
    onGenerateImage: (String, String) -> Unit,
    onEditImage: (Bitmap, String) -> Unit,
    onShareMedia: (File, String, String) -> Unit
) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf("1:1") }
    var isEditMode by remember { mutableStateOf(false) }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Play-compliant zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedBitmap = decodeScaledBitmap(context.contentResolver, uri)
            isEditMode = true
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isEditMode,
                    onClick = { isEditMode = false },
                    label = { Text("Create New") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan,
                        containerColor = SurfaceCardElevated,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("image_mode_create")
                )

                FilterChip(
                    selected = isEditMode,
                    onClick = { isEditMode = true },
                    label = { Text("Edit Photo") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                        selectedLabelColor = NeonPurple,
                        containerColor = SurfaceCardElevated,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("image_mode_edit")
                )
            }

            if (isEditMode) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("pick_photo_to_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Choose Photo",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pickedBitmap != null) "Change Selected Photo" else "Pick Photo from Gallery",
                        color = CyberCyan
                    )
                }

                pickedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected source image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1:1", "16:9", "9:16").forEach { ratio ->
                        FilterChip(
                            selected = aspectRatio == ratio,
                            onClick = { aspectRatio = ratio },
                            label = { Text(ratio) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan,
                                containerColor = SurfaceCardElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("image_ratio_${ratio.replace(':', '_')}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = {
                    Text(
                        if (isEditMode) "क्या बदलाव करना है? (Edit Prompt)" else "क्या इमेज बनानी है? (Prompt)",
                        fontSize = 12.sp
                    )
                },
                placeholder = {
                    Text(
                        if (isEditMode) "Add sunset sky & golden glow..." else "Futuristic Indian cyberpunk street at night...",
                        color = TextDim
                    )
                },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceCardElevated,
                    unfocusedContainerColor = SurfaceCardElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_prompt_input")
            )

            Button(
                onClick = {
                    val bmp = pickedBitmap
                    if (isEditMode && bmp != null) {
                        onEditImage(bmp, prompt)
                    } else {
                        onGenerateImage(prompt, aspectRatio)
                    }
                },
                enabled = imageState !is ImageStudioUiState.Loading &&
                    prompt.isNotBlank() &&
                    (!isEditMode || pickedBitmap != null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    disabledContainerColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("generate_image_button")
            ) {
                Text(
                    text = if (isEditMode) "Apply AI Edit (Nano Banana 2.1)" else "Generate Image (Nano Banana 2.1)",
                    color = DeepSpace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    when (imageState) {
        is ImageStudioUiState.Loading -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = CyberCyan)
                    Text(
                        text = "AI इमेज तैयार कर रही है (30–60 सेकंड लग सकते हैं)...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        is ImageStudioUiState.Success -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        bitmap = imageState.bitmap.asImageBitmap(),
                        contentDescription = "Generated AI image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Button(
                        onClick = { onShareMedia(imageState.file, "image/png", "Share JULI AI Image") },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("share_generated_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Image",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Image", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        is ImageStudioUiState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = StateError.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, StateError.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = imageState.message,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        is ImageStudioUiState.Idle -> Unit
    }
}

@Composable
private fun VideoStudioSection(
    videoState: VideoStudioUiState,
    hasVideoApiKey: Boolean,
    onGenerateVideo: (String, String, Boolean) -> Unit,
    onShareMedia: (File, String, String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var useFollowUp by remember { mutableStateOf(false) }

    val previousId = (videoState as? VideoStudioUiState.Success)?.interactionId

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasVideoApiKey) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StateError.copy(alpha = 0.14f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Video generation (gemini-omni-1.2-flash) के लिए AI Studio Secrets में VIDEO_STUDIO_API_KEY सेट करें।",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("16:9" to "16:9 Landscape", "9:16" to "9:16 Portrait").forEach { (ratio, label) ->
                    FilterChip(
                        selected = aspectRatio == ratio,
                        onClick = { aspectRatio = ratio },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CosmicMagenta.copy(alpha = 0.2f),
                            selectedLabelColor = CosmicMagenta,
                            containerColor = SurfaceCardElevated,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("video_ratio_${ratio.replace(':', '_')}")
                    )
                }
            }

            if (!previousId.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit previous video (Follow-up)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = useFollowUp,
                        onCheckedChange = { useFollowUp = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepSpace,
                            checkedTrackColor = CosmicMagenta
                        ),
                        modifier = Modifier.testTag("video_followup_switch")
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("वीडियो प्रॉम्प्ट (Text-to-Video)", fontSize = 12.sp) },
                placeholder = { Text("A cinematic drone shot over misty Himalayan valleys at sunrise...", color = TextDim) },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CosmicMagenta,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceCardElevated,
                    unfocusedContainerColor = SurfaceCardElevated
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_prompt_input")
            )

            Button(
                onClick = { onGenerateVideo(prompt, aspectRatio, useFollowUp) },
                enabled = videoState !is VideoStudioUiState.Loading && prompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CosmicMagenta,
                    disabledContainerColor = SurfaceBorder
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("generate_video_button")
            ) {
                Text(
                    text = if (useFollowUp) "Edit Video (Omni 1.2 Flash)" else "Generate Video (Omni 1.2 Flash)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    when (videoState) {
        is VideoStudioUiState.Loading -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, CosmicMagenta.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = CosmicMagenta)
                    Text(
                        text = "AI वीडियो जनरेट कर रही है... कृपया प्रतीक्षा करें।",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        is VideoStudioUiState.Success -> {
            VideoPlayerCard(
                videoFile = videoState.file,
                aspectRatio = videoState.aspectRatio,
                summaryText = videoState.summaryText,
                onShare = { onShareMedia(videoState.file, "video/mp4", "Share JULI AI Video") }
            )
        }

        is VideoStudioUiState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = StateError.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, StateError.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = videoState.message,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        is VideoStudioUiState.Idle -> Unit
    }
}

@Composable
private fun VideoPlayerCard(
    videoFile: File,
    aspectRatio: String,
    summaryText: String?,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoFile.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val ratioFloat = if (aspectRatio == "9:16") 9f / 16f else 16f / 9f

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CosmicMagenta.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratioFloat)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!summaryText.isNullOrBlank()) {
                Text(
                    text = summaryText,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("share_generated_video_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Video",
                    tint = DeepSpace,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Video (MP4)", color = DeepSpace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun decodeScaledBitmap(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
    maxDimension: Int = 1024
): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
        var sampleSize = 1
        while (opts.outWidth / sampleSize > maxDimension || opts.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    } catch (_: Exception) {
        null
    }
}

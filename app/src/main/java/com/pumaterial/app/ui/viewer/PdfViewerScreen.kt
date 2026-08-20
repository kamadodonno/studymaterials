package com.pumaterial.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumaterial.app.core.components.ErrorStateView
import com.pumaterial.app.core.components.LoadingView
import com.pumaterial.app.core.file.FileOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    title: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Natural 1:1 Zoom & Pan States
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val listState = rememberLazyListState()

    // Fast-scroll Scrubber States
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubberFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    // Track current visible page
    val currentPage by remember {
        derivedStateOf {
            if (isScrubbing) {
                ((scrubberFraction * (pageCount - 1)).roundToInt() + 1).coerceIn(1, max(1, pageCount))
            } else {
                (listState.firstVisibleItemIndex + 1).coerceAtMost(max(1, pageCount))
            }
        }
    }

    // Keep scrubber position updated during normal scrolling
    LaunchedEffect(listState.firstVisibleItemIndex, isScrubbing, pageCount) {
        if (!isScrubbing && pageCount > 1) {
            scrubberFraction = (listState.firstVisibleItemIndex.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(filePath, configuration.screenWidthDp, configuration.orientation) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    errorMessage = "PDF file not found on device."
                    isLoading = false
                    return@withContext
                }

                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)
                pageCount = renderer.pageCount

                val bitmaps = mutableListOf<Bitmap>()
                val displayMetrics = context.resources.displayMetrics
                val targetWidth = (displayMetrics.widthPixels * 1.5f).toInt().coerceIn(1080, 2160)

                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * aspectRatio).toInt()

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }

                renderer.close()
                fileDescriptor.close()

                withContext(Dispatchers.Main) {
                    pageBitmaps = bitmaps
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Unable to render document: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "Page $currentPage of $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Zoom Reset Button (visible when zoomed in)
                    if (scale > 1.05f) {
                        IconButton(
                            onClick = {
                                scale = 1.0f
                                offset = Offset.Zero
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOutMap,
                                contentDescription = "Fit to Screen",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Open in External App (PowerPoint, Google Docs, Drive, WPS)
                    IconButton(
                        onClick = {
                            val file = File(filePath)
                            if (file.exists()) {
                                FileOpener.openFile(context, file, "pdf")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in External App"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0F0F0F)) // Google Drive Dark PDF Viewer background
        ) {
            when {
                isLoading -> LoadingView(message = "Opening document...")
                errorMessage != null -> ErrorStateView(message = errorMessage ?: "Unknown error")
                pageBitmaps.isNotEmpty() -> {
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { containerSize = it.size }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        if (scale > 1.1f) {
                                            scale = 1.0f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.2f
                                            val maxOffsetX = (size.width * 1.2f) / 2f
                                            val maxOffsetY = (size.height * 1.2f) / 2f
                                            val targetOffsetX = (size.width / 2f - tapOffset.x)
                                            val targetOffsetY = (size.height / 2f - tapOffset.y)
                                            offset = Offset(
                                                targetOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                                                targetOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1.0f, 4.0f)
                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f

                                    // Natural 1:1 pan (no speed multiplier that causes wild overshooting)
                                    val newOffsetX = if (newScale > 1f) {
                                        (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    } else 0f

                                    val newOffsetY = if (newScale > 1f) {
                                        (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    } else 0f

                                    scale = newScale
                                    offset = Offset(newOffsetX, newOffsetY)
                                }
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = scale <= 1.05f, // Normal scroll at 1.0x, 2D pan when zoomed
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(pageBitmaps.size) { index ->
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Image(
                                        bitmap = pageBitmaps[index].asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Google Drive-Style Vertical Fast-Scroll Slider on Right Edge
                        if (pageCount > 1 && scale <= 1.05f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 40.dp, horizontal = 4.dp)
                                    .width(48.dp)
                                    .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
                            ) {
                                // Background Track line
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0x33FFFFFF))
                                )

                                // Draggable Scrubber Thumb & Floating Tooltip Bubble
                                val thumbOffsetDp = ((trackHeightPx - 50f) * scrubberFraction / context.resources.displayMetrics.density).dp

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(y = thumbOffsetDp.coerceAtLeast(0.dp))
                                        .pointerInput(pageCount) {
                                            detectVerticalDragGestures(
                                                onDragStart = { isScrubbing = true },
                                                onDragEnd = { isScrubbing = false },
                                                onDragCancel = { isScrubbing = false },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (trackHeightPx > 0f) {
                                                        val deltaFraction = dragAmount / trackHeightPx
                                                        scrubberFraction = (scrubberFraction + deltaFraction).coerceIn(0f, 1f)
                                                        val targetIndex = (scrubberFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                                                        coroutineScope.launch {
                                                            listState.scrollToItem(targetIndex)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    // Floating Page Bubble (visible while dragging)
                                    AnimatedVisibility(
                                        visible = isScrubbing,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shadowElevation = 6.dp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "Page $currentPage",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    // Fast-scroll Scrubber Thumb Pill
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isScrubbing) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(36.dp)
                                    ) {}
                                }
                            }
                        }

                        // Floating Bottom Page Pill Indicator (like Drive)
                        AnimatedVisibility(
                            visible = pageCount > 1 && !isScrubbing,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xCC000000),
                                contentColor = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$currentPage / $pageCount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

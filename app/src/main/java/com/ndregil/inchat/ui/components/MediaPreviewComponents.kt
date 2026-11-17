package com.ndregil.inchat.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Scale
import com.ndregil.inchat.util.MediaUrlUtils
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object InChatImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildLoader(appContext: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) InChat/1.0")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(appContext)
            .okHttpClient(okHttpClient)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .allowHardware(false)
            .build()
    }
}

private val MENTION_REGEX = Regex("""(?<!\S)@([A-Za-z0-9._-]+)""")

private sealed class RichTextSegment(val range: IntRange) {
    class UrlSeg(val normalizedUrl: String, val displayText: String, range: IntRange) : RichTextSegment(range)
    class MentionSeg(val nickname: String, val rawText: String, range: IntRange) : RichTextSegment(range)
}

fun buildRichAnnotatedText(
    text: String,
    linkColor: Color,
    textColor: Color,
    accentColor: Color = linkColor,
    myNickname: String = "",
    enableMentions: Boolean = false,
    enableQuotes: Boolean = false,
    onUrlClick: (String) -> Unit,
    onMentionClick: ((String) -> Unit)? = null
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    val urlMatches = MediaUrlUtils.extractUrlMatches(text)
    val mentionMatches = if (enableMentions) {
        MENTION_REGEX.findAll(text).filter { m ->
            urlMatches.none { url -> m.range.first in url.range || m.range.last in url.range }
        }.toList()
    } else {
        emptyList()
    }

    val segments = buildList {
        urlMatches.forEach { add(RichTextSegment.UrlSeg(it.normalizedUrl, it.displayUrl, it.range)) }
        mentionMatches.forEach { m ->
            val cleaned = MediaUrlUtils.trimTrailingUrlPunctuation(m.value)
            val nick = cleaned.removePrefix("@")
            if (nick.isNotBlank()) {
                val end = m.range.first + cleaned.length - 1
                add(RichTextSegment.MentionSeg(nick, cleaned, m.range.first..end))
            }
        }
    }.sortedBy { it.range.first }

    return buildAnnotatedString {
        var cursor = 0
        fun appendPlainSlice(slice: String) {
            if (slice.isEmpty()) return
            if (!enableQuotes || !slice.contains(">")) {
                append(slice)
                return
            }
            val lines = slice.split("\n")
            lines.forEachIndexed { idx, line ->
                if (line.trimStart().startsWith(">")) {
                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    ) {
                        append(line)
                    }
                } else {
                    append(line)
                }
                if (idx < lines.lastIndex) append("\n")
            }
        }

        for (seg in segments) {
            if (seg.range.first < cursor) continue
            if (seg.range.first > cursor) {
                appendPlainSlice(text.substring(cursor, seg.range.first))
            }
            when (seg) {
                is RichTextSegment.UrlSeg -> {
                    withLink(
                        LinkAnnotation.Url(
                            url = seg.normalizedUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            ),
                            linkInteractionListener = LinkInteractionListener { link ->
                                val target = (link as? LinkAnnotation.Url)?.url ?: seg.normalizedUrl
                                onUrlClick(target)
                            }
                        )
                    ) {
                        append(seg.displayText)
                    }
                }
                is RichTextSegment.MentionSeg -> {
                    val isMe = myNickname.isNotBlank() && seg.nickname.equals(myNickname, ignoreCase = true)
                    val mentionColor = if (isMe) {
                        if (textColor.luminance() > 0.5f) Color(0xFFFACC15) else Color(0xFFCA8A04)
                    } else {
                        accentColor
                    }
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "MENTION:${seg.nickname}",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = mentionColor,
                                    background = if (isMe) mentionColor.copy(alpha = 0.15f) else Color.Unspecified
                                )
                            ),
                            linkInteractionListener = LinkInteractionListener {
                                onMentionClick?.invoke(seg.nickname)
                            }
                        )
                    ) {
                        append(seg.rawText)
                    }
                }
            }
            cursor = seg.range.last + 1
        }

        if (cursor < text.length) {
            appendPlainSlice(text.substring(cursor))
        }
    }
}

@Composable
fun LinkifiedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    linkColor: Color = if (color.luminance() > 0.5f) Color(0xFF60A5FA) else Color(0xFF2563EB),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current

    val annotated = remember(text, linkColor, color) {
        buildRichAnnotatedText(
            text = text,
            linkColor = linkColor,
            textColor = color,
            onUrlClick = { url ->
                MediaUrlUtils.openExternalUrl(context, uriHandler, url, strings.cannotOpenUrlToast)
            }
        )
    }

    Text(
        text = annotated,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun InlineUrlImagePreviews(
    text: String,
    extraUrls: List<String> = emptyList(),
    mimeTypeHint: String? = null,
    modifier: Modifier = Modifier
) {
    val candidateUrls = remember(text, extraUrls, mimeTypeHint) {
        (extraUrls.mapNotNull { it.takeIf(String::isNotBlank)?.let(MediaUrlUtils::normalizeUrl) } +
            MediaUrlUtils.extractUrls(text))
            .distinct()
            .filter { MediaUrlUtils.shouldAttemptImagePreview(it, mimeTypeHint) }
            .take(5)
    }

    if (candidateUrls.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        candidateUrls.forEach { url ->
            UrlImagePreviewCard(
                url = url,
                mimeType = mimeTypeHint,
                isKnownImage = MediaUrlUtils.isKnownImageUrl(url, mimeTypeHint)
            )
        }
    }
}

@Composable
fun UrlImagePreviewCard(
    url: String,
    mimeType: String? = null,
    isKnownImage: Boolean = MediaUrlUtils.isKnownImageUrl(url, mimeType),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val strings = com.ndregil.inchat.ui.i18n.LocalAppStrings.current
    val normalizedUrl = remember(url) { MediaUrlUtils.normalizeUrl(url) }
    val imageLoader = remember(context) { InChatImageLoader.get(context) }

    var showFullscreen by remember(normalizedUrl) { mutableStateOf(false) }

    val imageRequest = remember(normalizedUrl) {
        ImageRequest.Builder(context)
            .data(normalizedUrl)
            .size(1024, 1024)
            .scale(Scale.FIT)
            .crossfade(true)
            .build()
    }

    val painter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader
    )
    val state = painter.state

    when (state) {
        is AsyncImagePainter.State.Success -> {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically()
            ) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { showFullscreen = true }
                        .testTag("image_preview_card")
                ) {
                    Image(
                        painter = painter,
                        contentDescription = strings.imagePreviewDesc,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(34.dp)
                            .clickable {
                                MediaUrlUtils.openExternalUrl(context, uriHandler, normalizedUrl, strings.cannotOpenUrlToast)
                            }
                            .testTag("open_image_url_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = normalizedUrl,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
        is AsyncImagePainter.State.Loading -> {
            if (isKnownImage) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            MediaUrlUtils.openExternalUrl(context, uriHandler, normalizedUrl, strings.cannotOpenUrlToast)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                }
            }
        }
        is AsyncImagePainter.State.Error -> {
            if (isKnownImage) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            MediaUrlUtils.openExternalUrl(context, uriHandler, normalizedUrl, strings.cannotOpenUrlToast)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = strings.imagePreviewDesc,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = normalizedUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = normalizedUrl,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        is AsyncImagePainter.State.Empty -> Unit
    }

    if (showFullscreen) {
        Dialog(
            onDismissRequest = { showFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { showFullscreen = false }
            ) {
                Image(
                    painter = painter,
                    contentDescription = strings.imagePreviewDesc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                IconButton(
                    onClick = { showFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.closePhotoBtn,
                        tint = Color.White
                    )
                }

                Button(
                    onClick = {
                        MediaUrlUtils.openExternalUrl(context, uriHandler, normalizedUrl, strings.cannotOpenUrlToast)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = normalizedUrl.substringAfter("://").substringBefore("/").ifBlank { normalizedUrl },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

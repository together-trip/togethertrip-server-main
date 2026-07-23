package com.togethertrip.main.triprecap.service.ai

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
@ConditionalOnProperty(
    prefix = "trip-recap.ai",
    name = ["provider"],
    havingValue = "stub",
    matchIfMissing = true,
)
class StubTripRecapGenerator : TripRecapGenerator {

    override fun generate(request: TripRecapGenerateRequest): TripRecapGenerateResult {
        val sceneCount = determineSceneCount(request)
        val scenes = (1..sceneCount).map { order ->
            val description = buildSceneDescription(request, order)
            TripRecapGeneratedScene(
                order = order,
                sceneDescription = description,
                imagePrompt = buildImagePrompt(
                    request = request,
                    sceneDescription = description,
                ),
                imageBytes = buildPlaceholderPng(
                    request = request,
                    order = order,
                ),
            )
        }

        return TripRecapGenerateResult(
            provider = "stub",
            model = "stub-trip-recap-v1",
            scenes = scenes,
        )
    }

    private fun determineSceneCount(request: TripRecapGenerateRequest): Int {
        val richness = request.places.size + request.expenseSignals.size + request.photoReferences.size
        return when {
            richness >= 10 -> 7
            richness >= 5 -> 5
            else -> 3
        }
    }

    private fun buildSceneDescription(
        request: TripRecapGenerateRequest,
        order: Int,
    ): String {
        val styleLabel = when (request.style) {
            TripRecapStyle.PHOTO -> "cinematic travel snapshot"
            TripRecapStyle.ILLUSTRATION -> "emotional travel illustration"
        }
        val place = request.places.getOrNull(order - 1)?.name
            ?: request.countries.getOrNull((order - 1) % request.countries.size.coerceAtLeast(1))?.countryName
            ?: request.tripTitle

        return "$styleLabel scene $order for ${request.memberCount} travelers around $place"
    }

    private fun buildImagePrompt(
        request: TripRecapGenerateRequest,
        sceneDescription: String,
    ): String {
        val baseStyle = when (request.style) {
            TripRecapStyle.PHOTO -> "cinematic realistic travel snapshot"
            TripRecapStyle.ILLUSTRATION -> "warm editorial travel illustration"
        }

        return listOf(
            baseStyle,
            sceneDescription,
            "vertical 9:16 composition",
            "no text, no letters, no captions, no logos",
            "no face close-up, no identifiable real person",
            "people only if natural, seen from behind or as silhouettes",
        ).joinToString(", ")
    }

    private fun buildPlaceholderPng(
        request: TripRecapGenerateRequest,
        order: Int,
    ): ByteArray {
        val width = 540
        val height = 960
        val palette = StubPalette.from(
            seed = "${request.tripTitle}:${request.style}:$order".hashCode(),
            style = request.style,
        )
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            graphics.paint = GradientPaint(
                0f,
                0f,
                palette.skyTop,
                0f,
                height.toFloat(),
                palette.skyBottom,
            )
            graphics.fillRect(0, 0, width, height)

            if (request.style == TripRecapStyle.PHOTO) {
                paintPhotoScene(graphics, width, height, palette, order)
            } else {
                paintIllustrationScene(graphics, width, height, palette, order)
            }
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    private fun paintPhotoScene(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        palette: StubPalette,
        order: Int,
    ) {
        graphics.color = palette.sun
        graphics.fill(Ellipse2D.Double(width * 0.62, height * 0.12, 132.0, 132.0))

        graphics.color = palette.distant
        graphics.fill(mountainPath(width, height, 0.46, 120, order * 13))
        graphics.color = palette.near
        graphics.fill(mountainPath(width, height, 0.56, 150, order * 19))

        graphics.paint = GradientPaint(
            0f,
            (height * 0.62).toFloat(),
            palette.waterTop,
            0f,
            height.toFloat(),
            palette.waterBottom,
        )
        graphics.fillRect(0, (height * 0.62).toInt(), width, (height * 0.38).toInt())

        graphics.stroke = BasicStroke(3f)
        graphics.color = Color(255, 255, 255, 70)
        repeat(8) { index ->
            val y = height * 0.68 + index * 34 + (order % 3) * 5
            graphics.drawLine(36, y.toInt(), width - 36, (y + 10).toInt())
        }
    }

    private fun paintIllustrationScene(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        palette: StubPalette,
        order: Int,
    ) {
        graphics.color = palette.sun
        graphics.fill(Ellipse2D.Double(width * 0.12, height * 0.10, 150.0, 150.0))

        graphics.color = palette.distant
        graphics.fill(roundHillPath(width, height, 0.42, 95))
        graphics.color = palette.near
        graphics.fill(roundHillPath(width, height, 0.54, 128))

        graphics.color = palette.waterTop
        graphics.fillRect(0, (height * 0.66).toInt(), width, (height * 0.34).toInt())
        graphics.color = Color(255, 255, 255, 72)
        repeat(5) { index ->
            val size = 42 + index * 8
            val x = 60 + index * 92 + (order % 2) * 18
            val y = height * 0.72 + index * 28
            graphics.fill(Ellipse2D.Double(x.toDouble(), y, size.toDouble(), 10.0))
        }
    }

    private fun mountainPath(
        width: Int,
        height: Int,
        baselineRatio: Double,
        peakHeight: Int,
        offset: Int,
    ): Path2D {
        val baseline = height * baselineRatio
        return Path2D.Double().apply {
            moveTo(0.0, baseline)
            lineTo(width * 0.18, baseline - peakHeight * 0.58 - offset % 18)
            lineTo(width * 0.36, baseline - peakHeight.toDouble())
            lineTo(width * 0.54, baseline - peakHeight * 0.50 - offset % 24)
            lineTo(width * 0.74, baseline - peakHeight * 0.86)
            lineTo(width.toDouble(), baseline - peakHeight * 0.38)
            lineTo(width.toDouble(), height.toDouble())
            lineTo(0.0, height.toDouble())
            closePath()
        }
    }

    private fun roundHillPath(
        width: Int,
        height: Int,
        baselineRatio: Double,
        lift: Int,
    ): Path2D {
        val baseline = height * baselineRatio
        return Path2D.Double().apply {
            moveTo(0.0, baseline)
            curveTo(
                width * 0.22,
                baseline - lift,
                width * 0.40,
                baseline - lift * 0.72,
                width * 0.58,
                baseline,
            )
            curveTo(
                width * 0.76,
                baseline + lift * 0.42,
                width * 0.88,
                baseline - lift * 0.48,
                width.toDouble(),
                baseline,
            )
            lineTo(width.toDouble(), height.toDouble())
            lineTo(0.0, height.toDouble())
            closePath()
        }
    }
}

private data class StubPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val distant: Color,
    val near: Color,
    val waterTop: Color,
    val waterBottom: Color,
) {
    companion object {
        fun from(
            seed: Int,
            style: TripRecapStyle,
        ): StubPalette {
            val shift = kotlin.math.abs(seed % 28)
            return when (style) {
                TripRecapStyle.PHOTO -> StubPalette(
                    skyTop = Color(84 + shift, 154, 210),
                    skyBottom = Color(248, 178 - shift / 2, 132),
                    sun = Color(255, 220, 150, 210),
                    distant = Color(78, 128 + shift / 2, 150),
                    near = Color(42, 86 + shift / 3, 104),
                    waterTop = Color(67, 145, 174),
                    waterBottom = Color(26, 92, 128 + shift / 3),
                )
                TripRecapStyle.ILLUSTRATION -> StubPalette(
                    skyTop = Color(246, 174, 138 + shift / 2),
                    skyBottom = Color(137, 188 - shift / 3, 205),
                    sun = Color(255, 235, 147),
                    distant = Color(127, 198 - shift / 3, 166),
                    near = Color(70, 146 + shift / 2, 132),
                    waterTop = Color(83, 160, 196),
                    waterBottom = Color(48, 112, 166),
                )
            }
        }
    }
}

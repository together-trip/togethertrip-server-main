package com.togethertrip.main.triprecap.service.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TripRecapImageModelRouterTest {

    private val router = DefaultTripRecapImageModelRouter()

    @Test
    fun `gpt image 2 aliases keep exact requested model`() {
        listOf("gpt-image-2", "gpt-image-2-2026-04-21").forEach { model ->
            assertEquals(
                TripRecapImageModelRoute(model, "flexible-size-edit-capable"),
                router.route(model),
            )
        }
    }

    @Test
    fun `legacy fixed size models are upgraded for exact vertical output`() {
        listOf("gpt-image-1", "gpt-image-1-mini").forEach { model ->
            assertEquals(
                TripRecapImageModelRoute("gpt-image-2", "legacy-model-upgrade-for-exact-9x16"),
                router.route(model),
            )
        }
    }

    @Test
    fun `unknown model is rejected instead of silently changing production behavior`() {
        assertFailsWith<IllegalArgumentException> {
            router.route("unknown-image-model")
        }
    }
}

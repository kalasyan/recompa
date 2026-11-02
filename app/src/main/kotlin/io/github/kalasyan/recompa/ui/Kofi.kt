package io.github.kalasyan.recompa.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Where people can support Recompa. */
private const val KOFI_URL = "https://ko-fi.com/joeyrt"

internal fun Context.openKofi() = startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)))

/** A coffee cup (Material's "local cafe"), for the Ko-fi button. */
internal val KofiCup: ImageVector by lazy {
    ImageVector.Builder("KofiCup", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M20,3H4v10c0,2.21 1.79,4 4,4h6c2.21,0 4,-1.79 4,-4v-3h2c1.11,0 2,-0.9 2,-2V5c0,-1.11 -0.89,-2 -2,-2z" +
                    "M20,8h-2V5h2v3zM2,21h18v-2H2v2z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}

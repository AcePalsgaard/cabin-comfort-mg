package dk.Android.Cpmonster.mgmanager.ui.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The user's own photo of their car, copied into app storage (scaled down, rotation applied). */
object CarPhoto {
    private const val MAX_EDGE = 1600

    private fun file(ctx: Context) = File(ctx.filesDir, "car_photo.jpg")

    /** Copies the picked picture. Returns false if it couldn't be read. */
    suspend fun save(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val s = MAX_EDGE.toFloat() / maxOf(w, h)
                if (s < 1f) d.setTargetSize((w * s).toInt(), (h * s).toInt())
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val tmp = File(ctx.filesDir, "car_photo.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            tmp.renameTo(file(ctx))
        }.getOrDefault(false)
    }

    fun delete(ctx: Context) {
        file(ctx).delete()
    }

    suspend fun load(ctx: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    }
}

/** Loads the photo again whenever [version] changes; null version = no photo. */
@Composable
fun rememberCarPhoto(version: Long?): ImageBitmap? {
    val ctx = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, version) {
        value = if (version == null) null else CarPhoto.load(ctx)
    }
    return photo
}

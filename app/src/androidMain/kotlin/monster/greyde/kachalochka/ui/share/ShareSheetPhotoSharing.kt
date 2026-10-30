package monster.greyde.kachalochka.ui.share

import android.content.Context

class ShareSheetPhotoSharing(
    private val context: Context,
) : PhotoSharing {
    override suspend fun share(jpeg: ByteArray) =
        shareImage(context, jpeg, "photo.jpg", "image/jpeg")
}

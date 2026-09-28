package monster.greyde.kachalochka.ui.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import monster.greyde.kachalochka.core.data.gym.PhotoImages
import monster.greyde.kachalochka.core.domain.gym.Photo
import okio.Buffer
import org.koin.compose.koinInject

private class PhotoFetcher(
    private val photo: Photo,
    private val options: Options,
    private val images: PhotoImages,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = checkNotNull(images.bytes(photo)) { "No bytes for photo ${photo.id.value}" }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val images: PhotoImages,
    ) : Fetcher.Factory<Photo> {
        override fun create(
            data: Photo,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = PhotoFetcher(data, options, images)
    }
}

// A photo's bytes never change, so its id is the whole cache key.
private class PhotoKeyer : Keyer<Photo> {
    override fun key(
        data: Photo,
        options: Options,
    ): String = data.id.value
}

/** One loader for the process, so every screen shares its memory cache. */
class PhotoLoaders(
    private val images: PhotoImages,
) {
    private var loader: ImageLoader? = null

    // Coil keeps only the application context, so no screen outlives itself in here.
    fun loader(context: PlatformContext): ImageLoader =
        loader ?: ImageLoader
            .Builder(context)
            .components {
                add(PhotoKeyer())
                add(PhotoFetcher.Factory(images))
            }.build()
            .also { loader = it }
}

@Composable
fun photoLoader(): ImageLoader {
    val loaders: PhotoLoaders = koinInject()
    val context = LocalPlatformContext.current
    return remember(loaders) { loaders.loader(context) }
}

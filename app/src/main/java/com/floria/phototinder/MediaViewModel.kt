package com.floria.phototinder

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "PhotoTinderPrefs"
private const val KEY_CURRENT_PHOTO_INDEX = "current_photo_index"
private const val KEY_CURRENT_VIDEO_INDEX = "current_video_index"
private const val KEY_TRASHED_PHOTO_URIS = "trashed_photo_uris"
private const val KEY_TRASHED_VIDEO_URIS = "trashed_video_uris"

sealed class Media(open val uri: String)
data class Photo(override val uri: String) : Media(uri)
data class Video(override val uri: String) : Media(uri)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val photos = mutableStateOf<List<Photo>>(emptyList())
    val videos = mutableStateOf<List<Video>>(emptyList())

    var currentPhotoIndex by mutableIntStateOf(0)
        private set
    var currentVideoIndex by mutableIntStateOf(0)
        private set

    private val _trashedPhotos = mutableStateOf<List<Photo>>(emptyList())
    val trashedPhotos: State<List<Photo>> = _trashedPhotos

    private val _trashedVideos = mutableStateOf<List<Video>>(emptyList())
    val trashedVideos: State<List<Video>> = _trashedVideos

    init {
        _trashedPhotos.value = (prefs.getStringSet(KEY_TRASHED_PHOTO_URIS, emptySet()) ?: emptySet()).map { Photo(it) }
        _trashedVideos.value = (prefs.getStringSet(KEY_TRASHED_VIDEO_URIS, emptySet()) ?: emptySet()).map { Video(it) }
    }

    fun loadMedia() {
        viewModelScope.launch {
            // Load photos and filter out trashed ones
            val allPhotos = loadFromGallery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ::Photo)
            val trashedPhotoUris = _trashedPhotos.value.map { it.uri }.toSet()
            photos.value = allPhotos.filter { it.uri !in trashedPhotoUris }
            Log.d("MediaViewModel", "Loaded ${photos.value.size} photos")
            val savedPhotoIndex = prefs.getInt(KEY_CURRENT_PHOTO_INDEX, 0)
            currentPhotoIndex = if (savedPhotoIndex < photos.value.size) savedPhotoIndex else 0

            // Load videos and filter out trashed ones
            val allVideos = loadFromGallery(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ::Video)
            val trashedVideoUris = _trashedVideos.value.map { it.uri }.toSet()
            videos.value = allVideos.filter { it.uri !in trashedVideoUris }
            Log.d("MediaViewModel", "Loaded ${videos.value.size} videos")
            val savedVideoIndex = prefs.getInt(KEY_CURRENT_VIDEO_INDEX, 0)
            currentVideoIndex = if (savedVideoIndex < videos.value.size) savedVideoIndex else 0
        }
    }

    private suspend fun <T : Media> loadFromGallery(contentUri: Uri, factory: (String) -> T): List<T> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<T>()
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            getApplication<Application>().contentResolver.query(contentUri, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(contentUri, id)
                    mediaList.add(factory(uri.toString()))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaViewModel", "Error loading media from $contentUri", e)
        }
        mediaList
    }

    fun restartPhotos() {
        if (photos.value.isNotEmpty()) {
            currentPhotoIndex = 0
            saveCurrentPhotoIndex(0)
        }
    }

    // --- Photo Actions ---
    fun swipePhotoLeft() {
        val photo = photos.value.getOrNull(currentPhotoIndex) ?: return
        _trashedPhotos.value += photo
        photos.value = photos.value - photo
        if (currentPhotoIndex >= photos.value.size && photos.value.isNotEmpty()) {
            currentPhotoIndex = 0
        }
        saveCurrentPhotoIndex(currentPhotoIndex)
        savePhotoTrashState()
    }

    fun swipePhotoRight() {
        if (photos.value.isNotEmpty()) {
            val newIndex = (currentPhotoIndex + 1) % photos.value.size
            saveCurrentPhotoIndex(newIndex)
            currentPhotoIndex = newIndex
        }
    }

    // --- Video Actions ---
    fun swipeVideoLeft() {
        val video = videos.value.getOrNull(currentVideoIndex) ?: return
        _trashedVideos.value += video
        videos.value = videos.value - video
        if (currentVideoIndex >= videos.value.size && videos.value.isNotEmpty()) {
            currentVideoIndex = 0
        }
        saveCurrentVideoIndex(currentVideoIndex)
        saveVideoTrashState()
    }

    fun swipeVideoRight() {
        if (videos.value.isNotEmpty()) {
            val newIndex = (currentVideoIndex + 1) % videos.value.size
            saveCurrentVideoIndex(newIndex)
            currentVideoIndex = newIndex
        }
    }

    // --- Trash Actions ---
    fun restoreFromTrash(media: Media) {
        when (media) {
            is Photo -> {
                _trashedPhotos.value -= media
                savePhotoTrashState()
            }
            is Video -> {
                _trashedVideos.value -= media
                saveVideoTrashState()
            }
        }
        loadMedia() // Reload all media to correctly place the restored item
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            val allTrash = _trashedPhotos.value + _trashedVideos.value
            allTrash.forEach { media ->
                try {
                    getApplication<Application>().contentResolver.delete(Uri.parse(media.uri), null, null)
                } catch (e: Exception) {
                    Log.e("MediaViewModel", "Error during permanent deletion of ${media.uri}", e)
                }
            }
            withContext(Dispatchers.Main) {
                _trashedPhotos.value = emptyList()
                _trashedVideos.value = emptyList()
                savePhotoTrashState()
                saveVideoTrashState()
            }
        }
    }

    // --- State Saving ---
    private fun saveCurrentPhotoIndex(index: Int) = prefs.edit().putInt(KEY_CURRENT_PHOTO_INDEX, index).apply()
    private fun saveCurrentVideoIndex(index: Int) = prefs.edit().putInt(KEY_CURRENT_VIDEO_INDEX, index).apply()
    private fun savePhotoTrashState() = prefs.edit().putStringSet(KEY_TRASHED_PHOTO_URIS, _trashedPhotos.value.map { it.uri }.toSet()).apply()
    private fun saveVideoTrashState() = prefs.edit().putStringSet(KEY_TRASHED_VIDEO_URIS, _trashedVideos.value.map { it.uri }.toSet()).apply()
}

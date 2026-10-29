package com.example.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.model.SharableItem
import com.example.model.ShareCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceMediaLoader(private val context: Context) {
    private val tag = "DeviceMediaLoader"

    suspend fun loadDeviceMedia(): List<SharableItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SharableItem>()

        try {
            // 1. Load Images
            result.addAll(queryCollection(
                collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                category = ShareCategory.PHOTOS,
                defaultMime = "image/jpeg",
                idColumn = MediaStore.Images.Media._ID,
                nameColumn = MediaStore.Images.Media.DISPLAY_NAME,
                sizeColumn = MediaStore.Images.Media.SIZE,
                mimeColumn = MediaStore.Images.Media.MIME_TYPE,
                dateColumn = MediaStore.Images.Media.DATE_ADDED
            ))

            // 2. Load Videos
            result.addAll(queryCollection(
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                category = ShareCategory.VIDEOS,
                defaultMime = "video/mp4",
                idColumn = MediaStore.Video.Media._ID,
                nameColumn = MediaStore.Video.Media.DISPLAY_NAME,
                sizeColumn = MediaStore.Video.Media.SIZE,
                mimeColumn = MediaStore.Video.Media.MIME_TYPE,
                dateColumn = MediaStore.Video.Media.DATE_ADDED
            ))

            // 3. Load Audio
            result.addAll(queryCollection(
                collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                category = ShareCategory.AUDIO,
                defaultMime = "audio/mpeg",
                idColumn = MediaStore.Audio.Media._ID,
                nameColumn = MediaStore.Audio.Media.DISPLAY_NAME,
                sizeColumn = MediaStore.Audio.Media.SIZE,
                mimeColumn = MediaStore.Audio.Media.MIME_TYPE,
                dateColumn = MediaStore.Audio.Media.DATE_ADDED
            ))
        } catch (e: Exception) {
            Log.w(tag, "Error querying MediaStore: ${e.message}")
        }

        result
    }

    private fun queryCollection(
        collectionUri: Uri,
        category: ShareCategory,
        defaultMime: String,
        idColumn: String,
        nameColumn: String,
        sizeColumn: String,
        mimeColumn: String,
        dateColumn: String
    ): List<SharableItem> {
        val items = mutableListOf<SharableItem>()
        val projection = arrayOf(idColumn, nameColumn, sizeColumn, mimeColumn, dateColumn)
        val sortOrder = "$dateColumn DESC"

        try {
            context.contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(idColumn)
                val nameIdx = cursor.getColumnIndexOrThrow(nameColumn)
                val sizeIdx = cursor.getColumnIndexOrThrow(sizeColumn)
                val mimeIdx = cursor.getColumnIndexOrThrow(mimeColumn)

                var count = 0
                while (cursor.moveToNext() && count < 25) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: "File_$id"
                    val size = cursor.getLong(sizeIdx).coerceAtLeast(1024L)
                    val mime = cursor.getString(mimeIdx) ?: defaultMime
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    items.add(
                        SharableItem(
                            id = "media_$id",
                            title = name,
                            subtitle = when (category) {
                                ShareCategory.PHOTOS -> "Device Photo"
                                ShareCategory.VIDEOS -> "Device Video"
                                ShareCategory.AUDIO -> "Device Audio"
                                else -> "Storage File"
                            },
                            sizeBytes = size,
                            mimeType = mime,
                            category = category,
                            uri = contentUri.toString(),
                            isSelected = false,
                            dateAdded = "On Device"
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to query $collectionUri: ${e.message}")
        }
        return items
    }
}

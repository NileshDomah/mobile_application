package com.king.mob_app_assignment.util

import androidx.exifinterface.media.ExifInterface
import java.io.File

// GPS + device-identifying tags are stripped before upload because the app already stores the
// authoritative GPS/compass/timestamp fields explicitly in Firestore; leaving them in EXIF would
// be redundant and would leak the capture device's make/model/software to anyone downloading
// the photo from Storage.
object ExifStripper {

    private val TAGS_TO_STRIP = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
    )

    fun stripSensitiveTags(photoFile: File): Boolean {
        return try {
            val exifInterface = ExifInterface(photoFile.absolutePath)
            TAGS_TO_STRIP.forEach { tag -> exifInterface.setAttribute(tag, null) }
            exifInterface.saveAttributes()
            true
        } catch (e: Exception) {
            false
        }
    }
}

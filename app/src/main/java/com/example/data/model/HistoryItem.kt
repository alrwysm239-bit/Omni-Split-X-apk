package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processing_history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val fileSize: Long,
    val fileType: String, // XAPK, APKS, APK, ZIP
    val architecture: String,
    val splitCount: Int,
    val isStandalone: Boolean,
    val hasPairIp: Boolean,
    val pairIpDetails: String,
    val outputPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // SUCCESS, FAILED, CANCELLED
)

package com.example.model

data class CrashReport(
    val reportId: String,
    val reportType: String,
    val timestamp: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val manufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val androidApiLevel: Int,
    val affectedComponent: String,
    val thread: String,
    val exceptionClass: String,
    val sanitizedMessage: String,
    val sanitizedStackTrace: String,
    val causeChain: String,
    val centralUnitState: String,
    val capabilityState: String
)

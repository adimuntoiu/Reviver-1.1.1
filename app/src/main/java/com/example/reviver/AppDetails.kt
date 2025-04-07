package com.example.reviver

data class AppDetails(
    val packageName: String,
    val appName: String,
    var timeLimit: Int,
    var mode: String,
    var maxOpens: Int = 0,          // New property for Mode 2
    var currentOpens: Int = 0,     // To track current count
    var backgroundUri: String? = null
)
package com.example.reviver

data class AppDetails(
    val packageName: String,
    val appName: String,
    var timeLimit: Int,
    var mode: String,
    var maxOpens: Int = 0,
    var currentOpens: Int = 0,
    var password: String? = ""
)
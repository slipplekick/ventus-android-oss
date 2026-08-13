package com.ventus.sys

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Hilt's entry point — the Android equivalent of a Flask app factory: this
// is where the dependency graph (repositories, Room database, Retrofit
// clients) gets wired up once at process start, rather than each screen
// constructing its own dependencies inline.
@HiltAndroidApp
class VentusApplication : Application()

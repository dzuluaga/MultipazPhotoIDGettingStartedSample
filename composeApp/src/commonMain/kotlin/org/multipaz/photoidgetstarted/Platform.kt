package org.multipaz.photoidgetstarted

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
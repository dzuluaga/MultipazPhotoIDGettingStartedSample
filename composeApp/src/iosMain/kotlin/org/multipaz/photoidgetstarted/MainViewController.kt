package org.multipaz.photoidgetstarted

import androidx.compose.ui.window.ComposeUIViewController

private val app = App.getInstance()

fun MainViewController() = ComposeUIViewController {
    app.Content()
}
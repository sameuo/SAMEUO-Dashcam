package com.sameuo.dashcam.ui.album

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import kotlin.PublishedApi
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@PublishedApi
internal fun Context.findActivity(): ComponentActivity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    error("No ComponentActivity in the context chain")
}

/** Activity-scoped ViewModel, shared across tabs and pushed screens. */
@Composable
inline fun <reified T : ViewModel> activityViewModel(): T {
    val context = LocalContext.current
    val owner: ViewModelStoreOwner = remember(context) { context.findActivity() }
    return viewModel(viewModelStoreOwner = owner)
}

/** Activity-scoped [LibraryViewModel], shared by tabs, grids, search and detail. */
@Composable
fun libraryViewModel(): LibraryViewModel = activityViewModel()

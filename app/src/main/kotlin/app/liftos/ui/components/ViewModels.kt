package app.liftos.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Creates (or restores) a ViewModel whose dependencies come from our hand-written container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline build: () -> VM): VM =
    viewModel<VM>(key = key, factory = viewModelFactory { initializer { build() } })

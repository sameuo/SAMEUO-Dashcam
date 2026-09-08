package com.sameuo.dashcam.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Concise ViewModel construction backed by [ServiceLocator] (no DI codegen). */
@Composable
inline fun <reified VM : ViewModel> sameuoVm(crossinline create: () -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create() } })

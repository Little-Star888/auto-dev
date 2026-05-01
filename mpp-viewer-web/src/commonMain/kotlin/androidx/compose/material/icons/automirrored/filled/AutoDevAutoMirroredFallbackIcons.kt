@file:Suppress("DEPRECATION")

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.vector.ImageVector

// Lightweight fallbacks for icons that otherwise require material-icons-extended.
val Icons.AutoMirrored.Filled.Article: ImageVector get() = Icons.Filled.List
val Icons.AutoMirrored.Filled.Chat: ImageVector get() = Icons.Filled.Email
val Icons.AutoMirrored.Filled.Help: ImageVector get() = Icons.Filled.Info

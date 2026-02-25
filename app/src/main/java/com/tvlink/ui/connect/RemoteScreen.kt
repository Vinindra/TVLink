package com.tvlink.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tvlink.ui.components.SubScreenTopBar

private object Key {
    const val HOME = 3; const val BACK = 4; const val MENU = 82
    const val DPAD_UP = 19; const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21; const val DPAD_RIGHT = 22; const val DPAD_CENTER = 23
    const val VOLUME_UP = 24; const val VOLUME_DOWN = 25; const val VOLUME_MUTE = 164
}

private val RemotePadSize = 252.dp
private val RemoteEdgeKeySize = 56.dp
private val RemoteCenterKeySize = 92.dp
private val RemoteActionKeySize = 64.dp

@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    var textInput by remember { mutableStateOf("") }
    var isTextMode by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SubScreenTopBar(title = "Remote Control", onBack = onBack)
        },
        bottomBar = {
            // ── Text input bar ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(124.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isTextMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            1.dp,
                            if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { isTextMode = !isTextMode }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Keyboard,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isTextMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (isTextMode) "Text Mode On" else "Text Mode Off",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTextMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (textInput.isEmpty()) {
                        Text("Type text to send to TV…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    BasicTextField(
                        value = textInput,
                        onValueChange = { newText ->
                            val oldText = textInput
                            textInput = newText
                            if (isTextMode) {
                                viewModel.sendTextAsKeyboard(oldText, newText)
                            }
                        },
                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Default),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (!isTextMode && textInput.isNotEmpty()) {
                                viewModel.sendText(textInput)
                                textInput = ""
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isTextMode && textInput.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .clickable(enabled = !isTextMode && textInput.isNotEmpty()) {
                            viewModel.sendText(textInput)
                            textInput = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Send, "Send",
                        modifier = Modifier.size(20.dp),
                        tint = if (!isTextMode && textInput.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer.copy(red = 0.22f, green = 0.12f, blue = 0.45f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Circular D-Pad ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(RemotePadSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    .size(RemoteEdgeKeySize).clip(CircleShape).clickable { viewModel.sendKey(Key.DPAD_UP) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.KeyboardArrowUp, "Up", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    .size(RemoteEdgeKeySize).clip(CircleShape).clickable { viewModel.sendKey(Key.DPAD_DOWN) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.KeyboardArrowDown, "Down", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
                    .size(RemoteEdgeKeySize).clip(CircleShape).clickable { viewModel.sendKey(Key.DPAD_LEFT) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.KeyboardArrowLeft, "Left", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
                    .size(RemoteEdgeKeySize).clip(CircleShape).clickable { viewModel.sendKey(Key.DPAD_RIGHT) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.KeyboardArrowRight, "Right", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(
                    modifier = Modifier.size(RemoteCenterKeySize).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { viewModel.sendKey(Key.DPAD_CENTER) },
                    contentAlignment = Alignment.Center
                ) { Text("OK", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleRemoteKey(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { viewModel.sendKey(Key.BACK) }
                CircleRemoteKey(Icons.Rounded.Home, "Home") { viewModel.sendKey(Key.HOME) }
                CircleRemoteKey(Icons.Rounded.Menu, "Menu") { viewModel.sendKey(Key.MENU) }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleRemoteKey(Icons.Rounded.VolumeMute, "Mute") { viewModel.sendKey(Key.VOLUME_MUTE) }
                CircleRemoteKey(Icons.Rounded.VolumeDown, "Vol -") { viewModel.sendKey(Key.VOLUME_DOWN) }
                CircleRemoteKey(Icons.Rounded.VolumeUp, "Vol +") { viewModel.sendKey(Key.VOLUME_UP) }
            }
        }
    }
}

@Composable
private fun CircleRemoteKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.size(RemoteActionKeySize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

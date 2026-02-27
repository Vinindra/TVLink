package com.tvlink.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tvlink.ui.components.ConnectionRequiredState

private val TerminalBg      = Color(0xFF060504)
private val TerminalOutput  = Color(0xFF9FD89F) // green   — stdout
private val TerminalError   = Color(0xFFE06C75) // red     — stderr / errors

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.history.size) {
        if (state.history.isNotEmpty()) {
            listState.scrollToItem(state.history.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        if (!state.isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                ConnectionRequiredState(
                    message = "Connect to a TV to use the ADB shell.",
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding()
            ) {
                // Terminal output
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TerminalBg)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.history) { entry ->
                        Row(modifier = Modifier.padding(start = if (entry.type == EntryType.COMMAND) 0.dp else 12.dp)) {
                            if (entry.type == EntryType.COMMAND) {
                                Text("$ ", color = MaterialTheme.colorScheme.outlineVariant, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            }
                            Text(
                                text = entry.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = when (entry.type) {
                                    EntryType.COMMAND -> MaterialTheme.colorScheme.primary
                                    EntryType.OUTPUT  -> TerminalOutput
                                    EntryType.ERROR   -> TerminalError
                                    EntryType.SYSTEM  -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                lineHeight = 22.sp // 1.7 * 13 = ~22
                            )
                        }
                    }
                }

                // Input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { viewModel.clearHistory() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⌫",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        BasicTextField(
                            value = state.currentInput,
                            onValueChange = { viewModel.updateInput(it) },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { viewModel.executeCommand() }
                            ),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (state.currentInput.isEmpty()) {
                                        Text(
                                            text = "Enter shell command...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    val isEnabled = state.currentInput.isNotBlank() && !state.isRunning
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isEnabled) 1f else 0.5f))
                            .clickable(enabled = isEnabled) { viewModel.executeCommand() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "→",
                            color = Color(0xFF1A0E0A),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

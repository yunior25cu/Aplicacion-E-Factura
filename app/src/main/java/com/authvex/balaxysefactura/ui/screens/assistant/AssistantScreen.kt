package com.authvex.balaxysefactura.ui.screens.assistant

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.authvex.balaxysefactura.BuildConfig
import com.authvex.balaxysefactura.ui.screens.assistant.cards.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCfeDetail: (Long) -> Unit = {},
    onNavigateToCollectionDetail: (Long) -> Unit = {}
) {
    val messages = viewModel.messages
    val isWaiting = viewModel.isWaiting
    val isSlow = viewModel.isSlow
    val isLoadingHistory = viewModel.isLoadingHistory
    val historyError = viewModel.historyError
    var showNewDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isWaiting) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Ícono Asistente Balaxys",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Asistente Balaxys", style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (messages.isNotEmpty()) {
                                showNewDialog = true
                            } else {
                                viewModel.newConversation()
                            }
                        },
                        enabled = !isWaiting && !isLoadingHistory
                    ) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = "Nueva conversación",
                            tint = if (!isWaiting && !isLoadingHistory) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoadingHistory) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (!historyError.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = historyError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.newConversation() },
                            enabled = !isWaiting
                        ) {
                            Text("Empezar una nueva", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Messages / Empty State Area (Weight 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isWaiting && !isLoadingHistory) {
                    // Empty State
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Asistente",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "¿Qué querés saber de tu empresa?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hacé preguntas en lenguaje natural sobre ventas, deudas, stock o cobros.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Messages List
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (isWaiting) {
                            item {
                                ThinkingBubble(isSlow = isSlow)
                            }
                        }

                        items(messages.reversed(), key = { it.id }) { msg ->
                            val msgIndex = messages.indexOf(msg)
                            val isLastHistoryMsg = msg.isFromHistory && (msgIndex == messages.size - 1 || !messages[msgIndex + 1].isFromHistory)

                            Column(modifier = Modifier.fillMaxWidth()) {
                                MessageBubble(
                                    message = msg,
                                    viewModel = viewModel,
                                    onNavigateToCfeDetail = onNavigateToCfeDetail,
                                    onNavigateToCollectionDetail = onNavigateToCollectionDetail,
                                    onRetry = { viewModel.retry(msg.id) }
                                )
                                if (isLastHistoryMsg) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HorizontalDivider(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "Conversación anterior",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                        HorizontalDivider(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = viewModel.input,
                        onValueChange = { viewModel.onInputChanged(it) },
                        placeholder = { Text("Escribí tu pregunta...") },
                        maxLines = 5,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.send(viewModel.input) },
                        enabled = viewModel.input.isNotBlank() && !isWaiting
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar mensaje",
                            tint = if (viewModel.input.isNotBlank() && !isWaiting) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("Nueva conversación") },
            text = { Text("¿Deseás reiniciar la conversación actual? Se borrará el historial de mensajes en pantalla.") },
            confirmButton = {
                TextButton(onClick = {
                    showNewDialog = false
                    viewModel.newConversation()
                }) {
                    Text("Reiniciar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageUi,
    viewModel: AssistantViewModel,
    onNavigateToCfeDetail: (Long) -> Unit,
    onNavigateToCollectionDetail: (Long) -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.role == ChatRole.USER
    val context = LocalContext.current
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = screenWidthDp * if (isUser) 0.85f else 0.90f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SelectionContainer {
                    if (isUser) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        SimpleMarkdown(
                            content = message.text,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isUser && message.tarjetas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        message.tarjetas.forEachIndexed { cardIdx, tarjeta ->
                            val cardModel = remember(tarjeta) { buildCard(tarjeta) }
                            val actionKey = "${message.id}_${cardIdx}"
                            val executionState = viewModel.getActionExecutionState(actionKey)

                            AiCardView(
                                model = cardModel,
                                executionState = executionState,
                                onExecuteConfirm = {
                                    cardModel.confirmAction?.let { act ->
                                        viewModel.executeCardAction(actionKey, act)
                                    }
                                },
                                onCancel = {
                                    viewModel.cancelCardAction(actionKey)
                                },
                                onOpenLink = { dest ->
                                    when (dest) {
                                        is ErpLinkDestination.NativeCfeDetail -> onNavigateToCfeDetail(dest.id)
                                        is ErpLinkDestination.NativeCollectionDetail -> onNavigateToCollectionDetail(dest.id)
                                        is ErpLinkDestination.ExternalWeb -> {
                                            try {
                                                val customTabsIntent = CustomTabsIntent.Builder().build()
                                                customTabsIntent.launchUrl(context, Uri.parse(dest.url))
                                            } catch (_: Exception) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dest.url))
                                                context.startActivity(intent)
                                            }
                                        }
                                        is ErpLinkDestination.Ignored -> {}
                                    }
                                },
                                webBaseUrl = BuildConfig.WEB_BASE_URL
                            )
                        }
                    }
                }

                if (message.status == MessageStatus.PENDING_ERROR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.errorText ?: "Error al enviar mensaje",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reintentar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reintentar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(isSlow: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "Thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Pensando",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSlow) "Consultando Balaxys, puede tardar unos segundos…" else "Pensando…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

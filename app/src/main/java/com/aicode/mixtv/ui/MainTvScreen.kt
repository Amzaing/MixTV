package com.aicode.mixtv.ui

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.aicode.mixtv.model.PlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun MainTvScreen(viewModel: TvPlaybackViewModel) {
    val channelList by viewModel.channels.collectAsState()
    val playState by viewModel.playbackState.collectAsState()
    val currentIndex by viewModel.currentChannelIndex.collectAsState()
    val isConfigInitialized by viewModel.isConfigInitialized.collectAsState()

    var isListVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var autoHideJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    val currentFocusRequester = remember { FocusRequester() }

    // 🎯 静态无帧过渡计算：左侧 OSD 避让间距
    val osdLeftPadding = if (isListVisible) 330.dp else 40.dp

    fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        if (isListVisible) {
            autoHideJob = coroutineScope.launch {
                delay(5000)
                isListVisible = false
            }
        }
    }

    LaunchedEffect(isListVisible, channelList, currentIndex) {
        resetAutoHideTimer()
        if (isListVisible && channelList.isNotEmpty() && currentIndex != -1) {
            delay(100)
            try {
                currentFocusRequester.requestFocus()
                listState.scrollToItem(currentIndex)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (!isConfigInitialized) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.Cyan)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "正在初始化配置流...", color = Color.White, fontSize = 16.sp)
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) resetAutoHideTimer()
                false
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!isListVisible && channelList.isNotEmpty()) { isListVisible = true; true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!isListVisible && channelList.isNotEmpty()) { viewModel.changeChannelOffset(-1); true } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isListVisible && channelList.isNotEmpty()) { viewModel.changeChannelOffset(1); true } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // LAYER 1: ExoPlayer 全屏底图
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    player = viewModel.player
                }
            },
            modifier = Modifier.fillMaxSize().clickable { if (channelList.isNotEmpty()) isListVisible = !isListVisible }
        )

        // LAYER 2: 悬浮半透明侧边台单列表
        AnimatedVisibility(
            visible = isListVisible && channelList.isNotEmpty(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(300.dp) // 🎯【微调】：调整为黄金比例宽度 300.dp 极其紧凑
                    .fillMaxHeight()
                    .background(Color(0xCC111111)) // 更加深沉高级的磨砂黑背景
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "📺 MixTV 聚合直播",
                        color = Color.Cyan,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }

                itemsIndexed(channelList) { index, channel ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isTargetAnchor = if (currentIndex != -1) index == currentIndex else index == 0
                    val itemModifier = if (isTargetAnchor) Modifier.focusRequester(currentFocusRequester) else Modifier

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp) // 🎯【微调】：加高到 52.dp，大屏按键手感大增
                            .then(itemModifier)
                            .background(
                                color = if (isFocused) Color(0xFF2C2C2E) else if (index == currentIndex) Color(0x2200FFFF) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 2.dp,
                                color = if (isFocused) Color.Cyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                ) {
                                    viewModel.playChannel(index)
                                    isListVisible = false
                                    true
                                } else false
                            }
                            .clickable {
                                viewModel.playChannel(index)
                                isListVisible = false
                            },
                        contentAlignment = Alignment.Center // 🎯【微调】：内容水平垂直绝对居中！
                    ) {
                        Text(
                            text = channel.name,
                            color = if (isFocused) Color.Cyan else if (index == currentIndex) Color.Cyan else Color.White,
                            fontSize = 18.sp, // 🎯【微调】：频道字号放大到 18.sp，沙发视觉更清晰
                            textAlign = TextAlign.Center, // 🎯 文字自身居中对齐
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // LAYER 3: 菊花加载器
        if (playState is PlaybackState.Loading || channelList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Cyan)
                    if (channelList.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "📺 首次安装正在疯狂网络聚合中...\n请稍候 2~3 秒，精彩马上呈现！",
                            color = Color.Cyan,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // LAYER 4: 全时常驻弹窗 OSD
        if (channelList.isNotEmpty()) {
            val isPlaying = playState is PlaybackState.Playing
            val shouldShowOsd = !isPlaying || isListVisible

            AnimatedVisibility(
                visible = shouldShowOsd,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, start = osdLeftPadding, end = 40.dp)
                        .background(Color(0xEE111111), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val chName = channelList.getOrNull(currentIndex)?.name ?: "未知频道"

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = chName, color = Color.White, fontSize = 20.sp)
                                if (playState is PlaybackState.Loading) {
                                    QuantumSourceTip(strategyManager = viewModel.strategyManager)
                                }
                            }

                            val statusText = when (playState) {
                                is PlaybackState.Loading -> "⏳ 正在建立连接，若失败将自动降级切源..."
                                is PlaybackState.Error -> "🚨 当前所有可用配置源全军覆没，请检查网络或更新 urls.txt"
                                is PlaybackState.Playing -> "🟢 已成功连接 - 稳定播放中"
                                else -> "📡 正在同步播放源数据..."
                            }
                            Text(text = statusText, color = Color.LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }

                        val activeSourceUrl = viewModel.strategyManager.getActiveSource()?.url ?: ""
                        if (activeSourceUrl.isNotEmpty()) {
                            val protocol = when {
                                activeSourceUrl.startsWith("rtsp", true) -> "RTSP 单播"
                                activeSourceUrl.startsWith("rtp", true) -> "RTP 组播"
                                else -> "HLS/HTTP"
                            }
                            Text(
                                text = "内核协议: $protocol",
                                color = Color.Cyan,
                                fontSize = 13.sp,
                                modifier = Modifier.background(Color(0x2200FFFF), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuantumSourceTip(strategyManager: com.aicode.mixtv.manager.PlaybackStrategyManager) {
    val safeSourceIndex by remember {
        derivedStateOf { strategyManager.currentSourceIndex + 1 }
    }
    Box(modifier = Modifier.wrapContentSize()) {
        Text(
            text = " (当前正尝试第 ${safeSourceIndex} 个源)",
            color = Color.Yellow,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
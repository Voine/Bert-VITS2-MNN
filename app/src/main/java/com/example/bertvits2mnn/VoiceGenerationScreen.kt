package com.example.bertvits2mnn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceGenerationScreen(viewModel: VoiceViewModel = viewModel()) {
    // 从 ViewModel 获取 UI 状态（假设 ViewModel 已经实现这些状态和方法）
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(initialValue = fakeUIState)
    val inputText = uiState.inputText            // 当前输入的文本
    val selectedCharacter = uiState.selectedCharacter  // 当前选中的角色名称
    val isLoading = uiState.isLoading            // 加载状态
    val characters = uiState.characters          // 静态角色列表\
    val lengthScale = uiState.currentLengthScale
    val logcat = uiState.logcat

    // 本地状态：控制角色选择底部弹窗的显示
    var showCharacterSheet by remember { mutableStateOf(false) }

    var showAudioScaleSheet by remember { mutableStateOf(false) }

    // UI 布局
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            // 文本输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { newText -> viewModel.updateInputText(newText) },
                label = { Text("输入合成文本") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 角色选择按钮，显示当前选中角色名称（若未选择则提示选择）
            Button(
                onClick = { showCharacterSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (selectedCharacter.isEmpty()) "选择角色" else "当前角色：$selectedCharacter")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 角色选择按钮，显示当前选中角色名称（若未选择则提示选择）
            Button(
                onClick = { showAudioScaleSheet = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "当前语音缩放系数：$lengthScale")
            }

            Spacer(modifier = Modifier.height(16.dp))
            val keyboardController = LocalSoftwareKeyboardController.current
            // “生成”按钮，点击触发音频推理
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.startAudioInference(inputText)  // 调用 ViewModel 中的生成音频推理方法
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "生成")
            }

            //logcat area
            Spacer(modifier = Modifier.height(16.dp))
            val listState = rememberLazyListState()

            LaunchedEffect(logcat) {
                val lines = logcat.split("\n")
                if (lines.isNotEmpty()) {
                    listState.scrollToItem(lines.size - 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                LazyColumn(state = listState) {
                    val lines = logcat.split("\n")
                    items(lines) { line ->
                        Text(line, fontSize = 16.sp)
                    }
                }
            }
        }

        // 角色选择底部弹窗（Modal Bottom Sheet）
        if (showCharacterSheet) {
            CharacterPickerGrid(
                characters = characters,
                selectedCharacter = selectedCharacter,
                onCharacterSelect = { character ->
                    viewModel.selectCharacter(character)  // 更新选中的角色
                },
                onDismiss = { showCharacterSheet = false }  // 关闭弹窗
            )
        }

        if (showAudioScaleSheet) {
            var textFieldValue by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { showAudioScaleSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("设置语音缩放系数", fontSize = 20.sp, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            error = false
                        },
                        label = { Text("请输入大于 0 的浮点数") },
                        isError = error,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) {
                        Text("请输入合法的数值", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val value = textFieldValue.toFloatOrNull()
                            if (value != null && value > 0f) {
                                viewModel.updateLengthScale(value)
                                showAudioScaleSheet = false
                            } else {
                                error = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确认")
                    }
                }
            }
        }


        // Loading 遮罩层：在 isLoading 为 true 时显示
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // 半透明背景覆盖全屏，居中显示加载指示器
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(enabled = true, onClick = { /* 拦截点击事件 */ }, indication = null, interactionSource = remember { MutableInteractionSource() })
            ) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.loadingHint,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CharacterPickerGrid(
    characters: List<String>,
    selectedCharacter: String,
    onCharacterSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(characters.size) { characterName ->
                val characterName = characters[characterName]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // 保持正方形形状
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (characterName == selectedCharacter) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable {
                            onCharacterSelect(characterName)
                            onDismiss()
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = characterName,
                            fontSize = 14.sp,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (characterName == selectedCharacter) {
                            // 小圆点标记选中
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}



val fakeUIState = UIState(
    inputText = "你好，我是一个合成语音的应用。",
    selectedCharacter = "角色A",
    isLoading = false,
    loadingHint = "正在生成语音...",
    characters = listOf(
        "角色A",
        "角色B",
        "角色C",
        "角色D",
        "角色E",
        "角色F",
        "角色G",
        "角色H",
        "角色I",
        "角色J",
        "角色K",
        "角色L",
        "角色M",
        "角色N",
        "角色O",
        "角色P",
        "角色Q",
        "角色R",
        "角色S",
        "角色T",
        "角色U",
        "角色V",
        "角色W",
        "角色X",
        "角色Y",
        "角色Z"
    ),
    logcat = "日志1\n日志2\n日志3\n日志4\n日志5\n日志6\n日志7\n日志8\n日志9\n日志10",
)

@Preview(showBackground = true)
@Composable
fun VoiceGenerationScreenPreview() {
    // 预览时使用默认的 ViewModel
    VoiceGenerationScreen(viewModel = viewModel()) // 这里可以传入一个假的 ViewModel 或者使用默认的 ViewModel
}

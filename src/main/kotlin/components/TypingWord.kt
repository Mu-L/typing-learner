package components

import LocalCtrl
import Settings
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import data.Caption
import data.VocabularyType
import data.Word
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import player.*
import state.AppState
import state.TypingType
import theme.createColors
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent
import java.awt.*
import java.io.File
import java.time.Duration
import java.util.*
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.concurrent.schedule

/**
 * 应用程序的核心组件
 * @param state 应用程序的状态
 * @param videoBounds 视频播放窗口的位置和大小
 */
@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalAnimationApi::class, ExperimentalSerializationApi::class
)
@Composable
fun TypingWord(
    window: ComposeWindow,
    title: String,
    audioPlayer: AudioPlayerComponent,
    state: AppState,
    videoBounds: Rectangle,
) {

    /** 协程构建器 */
    val scope = rememberCoroutineScope()

    /**  处理拖放文件的函数 */
    val transferHandler = createTransferHandler(
        showWrongMessage = { message ->
            JOptionPane.showMessageDialog(window, message)
        },
        parseImportFile = { files ->
            val file = files.first()
            scope.launch {
                if (file.extension == "json") {
                    if (state.typingWord.vocabularyPath != file.absolutePath) {
                        val index = state.findVocabularyIndex(file)
                        state.changeVocabulary(file,index)
                    } else {
                        JOptionPane.showMessageDialog(window, "词库已打开")
                    }

                } else if (file.extension == "mkv") {
                    JOptionPane.showMessageDialog(window, "如果想打开 MKV 视频文件抄写字幕，\n需要先切换到抄写字幕界面，\n如果想生成词库需要先打开生成词库界面。")
                } else {
                    JOptionPane.showMessageDialog(window, "只能读取 json 格式的词库")
                }
            }
        }
    )
    window.transferHandler = transferHandler

    Box(Modifier.background(MaterialTheme.colors.background)) {
        Row {
            TypingWordSidebar(state)
            if (state.openSettings) {
                val topPadding = if (isMacOS()) 30.dp else 0.dp
                Divider(Modifier.fillMaxHeight().width(1.dp).padding(top = topPadding))
            }
            Box(Modifier.fillMaxSize()) {
                val endPadding = 0.dp
                if (isMacOS()) {
                    MacOSTitle(
                        title = title,
                        window = window,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    )
                }

                if (state.vocabulary.wordList.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.Center)
                            .padding(end = endPadding,bottom = 58.dp)
                    ) {

                        /** 当前正在学习的单词 */
                        val currentWord = state.getCurrentWord()

                        /** 单词发音的本地路径 */
                        val audioPath = getAudioPath(
                            word = currentWord.value,
                            audioSet = state.audioSet,
                            addToAudioSet = {state.audioSet.add(it)},
                            pronunciation = state.typingWord.pronunciation
                        )

                        /** 是否正在播放单词发音 */
                        var isPlayingAudio by remember { mutableStateOf(false) }

                        /**
                         * 用快捷键播放视频时被调用的回调函数
                         * @param playTriple 视频播放参数，Caption 表示要播放的字幕，String 表示视频的地址，Int 表示字幕的轨道 ID。
                         */
                        @OptIn(ExperimentalSerializationApi::class)
                        val shortcutPlay: (playTriple: Triple<Caption, String, Int>?) -> Unit = { playTriple ->
                            if (playTriple != null) {
                                if (!state.isPlaying) {
                                    scope.launch {
                                        val file = File(playTriple.second)
                                        if (file.exists()) {
                                            state.isPlaying = true
                                            play(
                                                window = state.videoPlayerWindow,
                                                setIsPlaying = { state.isPlaying = it },
                                                state.global.videoVolume,
                                                playTriple,
                                                state.videoPlayerComponent,
                                                videoBounds
                                            )
                                        }
                                    }

                                }
                            }
                        }

                        /** 处理全局快捷键的回调函数 */
                        val globalKeyEvent: (KeyEvent) -> Boolean = {
                            when {
                                (it.isCtrlPressed && it.key == Key.A && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.isAuto = !state.typingWord.isAuto
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.D && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.global.isDarkTheme = !state.global.isDarkTheme
                                        state.colors = createColors(state.global.isDarkTheme, state.global.primaryColor)
                                        state.saveGlobalState()
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.P && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.phoneticVisible = !state.typingWord.phoneticVisible
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.L && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.morphologyVisible = !state.typingWord.morphologyVisible
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.E && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.definitionVisible = !state.typingWord.definitionVisible
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.K && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.translationVisible = !state.typingWord.translationVisible
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.T && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.global.type = TypingType.SUBTITLES
                                        state.saveGlobalState()
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.V && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.wordVisible = !state.typingWord.wordVisible
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }

                                (it.isCtrlPressed && it.key == Key.J && it.type == KeyEventType.KeyUp) -> {
                                    if (!isPlayingAudio) {
                                        playAudio(
                                            audioPath = audioPath,
                                            volume = state.global.audioVolume,
                                            audioPlayerComponent = audioPlayer,
                                            changePlayerState = { isPlaying -> isPlayingAudio = isPlaying },
                                            setIsAutoPlay = {}
                                        )
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.isShiftPressed && it.key == Key.Z && it.type == KeyEventType.KeyUp) -> {
                                    if (state.vocabulary.type == VocabularyType.DOCUMENT) {
                                        val playTriple = getPayTriple(currentWord, 0)
                                        shortcutPlay(playTriple)
                                    } else {
                                        val caption = state.getCurrentWord().captions[0]
                                        val playTriple =
                                            Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                                        shortcutPlay(playTriple)
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.isShiftPressed && it.key == Key.X && it.type == KeyEventType.KeyUp) -> {
                                    if (state.getCurrentWord().externalCaptions.size >= 2) {
                                        val playTriple = getPayTriple(currentWord, 1)
                                        shortcutPlay(playTriple)

                                    } else if (state.getCurrentWord().captions.size >= 2) {
                                        val caption = state.getCurrentWord().captions[1]
                                        val playTriple =
                                            Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                                        shortcutPlay(playTriple)
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.isShiftPressed && it.key == Key.C && it.type == KeyEventType.KeyUp) -> {
                                    if (state.getCurrentWord().externalCaptions.size >= 3) {
                                        val playTriple = getPayTriple(currentWord, 2)
                                        shortcutPlay(playTriple)
                                    } else if (state.getCurrentWord().captions.size >= 3) {
                                        val caption = state.getCurrentWord().captions[2]
                                        val playTriple =
                                            Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                                        shortcutPlay(playTriple)
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.S && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.subtitlesVisible = !state.typingWord.subtitlesVisible
                                        if (!state.isDictation) {
                                            state.saveTypingWordState()
                                        }
                                    }
                                    true
                                }

                                (it.isCtrlPressed && it.key == Key.M && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.global.isPlayKeystrokeSound = !state.global.isPlayKeystrokeSound
                                        state.saveGlobalState()
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.W && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.isPlaySoundTips = !state.typingWord.isPlaySoundTips
                                        state.saveTypingWordState()
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.One && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.openSettings = !state.openSettings
                                    }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.N && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {
                                        state.typingWord.speedVisible = !state.typingWord.speedVisible
                                        state.saveTypingWordState()
                                    }
                                    true
                                }
                                else -> false
                            }

                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .onKeyEvent { globalKeyEvent(it) }
                                .width(intrinsicSize = IntrinsicSize.Max)
                                .background(MaterialTheme.colors.background)
                                .focusable(true)
                        ) {

                            /** 单词输入框里的字符串*/
                            var wordTextFieldValue by remember { mutableStateOf("") }

                            /** 单词输入框的焦点请求器*/
                            val wordFocusRequester = remember { FocusRequester() }

                            /** 第一条字幕的输入字符串*/
                            var captionsTextFieldValue1 by remember { mutableStateOf("") }

                            /** 第二条字幕的输入字符串*/
                            var captionsTextFieldValue2 by remember { mutableStateOf("") }

                            /** 第三条字幕的输入字符串*/
                            var captionsTextFieldValue3 by remember { mutableStateOf("") }

                            /** 字幕输入框焦点请求器*/
                            val (focusRequester1,focusRequester2,focusRequester3) = remember { FocusRequester.createRefs() }

                            /** 单词输入框输入的结果*/
                            val wordTypingResult = remember { mutableStateListOf<Pair<Char, Boolean>>() }

                            /** 字幕输入框的结果 */
                            val captionsTypingResultMap =
                                remember { mutableStateMapOf<Int, MutableList<Pair<Char, Boolean>>>() }

                            /** 显示本章节已经完成对话框 */
                            var showChapterFinishedDialog by remember { mutableStateOf(false) }

                            /** 显示整个词库已经学习完成对话框 */
                            var isVocabularyFinished by remember { mutableStateOf(false) }

                            /** 显示编辑单词对话框 */
                            var showEditWordDialog by remember { mutableStateOf(false) }

                            val monospace by remember { mutableStateOf(FontFamily(Font("font/Inconsolata-Regular.ttf", FontWeight.Normal, FontStyle.Normal))) }

                            /** 播放错误音效 */
                            val playBeepSound = {
                                if (state.typingWord.isPlaySoundTips) {
                                    playSound("audio/beep.wav", state.typingWord.soundTipsVolume)
                                }
                            }

                            /** 播放成功音效 */
                            val playSuccessSound = {
                                if (state.typingWord.isPlaySoundTips) {
                                    playSound("audio/hint.wav", state.typingWord.soundTipsVolume)
                                }
                            }

                            /** 播放整个章节完成时音效 */
                            val playChapterFinished = {
                                if (state.typingWord.isPlaySoundTips) {
                                    playSound("audio/Success!!.wav", state.typingWord.soundTipsVolume)
                                }
                            }

                            /** 播放按键音效 */
                            val playKeySound = {
                                if (state.global.isPlayKeystrokeSound) {
                                    playSound("audio/keystroke.wav", state.global.keystrokeVolume)
                                }
                            }

                            /**
                             * 当用户在默写模式按 enter 调用的回调，
                             * 在默写模式跳过单词也算一次错误
                             */
                            val dictationSkipCurrentWord: () -> Unit = {
                                if (state.wordCorrectTime == 0) {
                                    state.chapterWrongTime++
                                    val dictationWrongTime = state.dictationWrongWords[currentWord]
                                    if (dictationWrongTime == null) {
                                        state.dictationWrongWords[currentWord] = 1
                                    }
                                }
                            }

                            /** 焦点切换到单词输入框*/
                            val jumpToWord:() -> Unit = {
                                wordFocusRequester.requestFocus()
                            }

                            /** 焦点切换到抄写字幕*/
                            val jumpToCaptions:() -> Unit = {
                                if(currentWord.captions.isNotEmpty()){
                                    focusRequester1.requestFocus()
                                }
                            }

                            /** 切换下一个单词 */
                            val toNext: () -> Unit = {
                                scope.launch {
                                    wordTypingResult.clear()
                                    wordTextFieldValue = ""
                                    captionsTypingResultMap.clear()
                                    captionsTextFieldValue1 = ""
                                    captionsTextFieldValue2 = ""
                                    captionsTextFieldValue3 = ""
                                    state.wordCorrectTime = 0
                                    state.wordWrongTime = 0
                                    if (state.isDictation) {
                                        if ((state.dictationIndex + 1) % state.dictationWords.size == 0) {
                                            /**
                                             * 在默写模式，闭着眼睛听写单词时，刚拼写完单词，就播放这个声音感觉不好，
                                             * 在非默写模式下按Enter键就不会有这种感觉，因为按Enter键，
                                             * 自己已经输入完成了，有一种期待，预测到了将会播放提示音。
                                             */
                                            Timer("playChapterFinishedSound", false).schedule(1000) {
                                                playChapterFinished()
                                            }
                                            showChapterFinishedDialog = true

                                        } else state.dictationIndex++
                                    } else {
                                        when {
                                            (state.typingWord.index == state.vocabulary.size - 1) -> {
                                                isVocabularyFinished = true
                                                playChapterFinished()
                                                showChapterFinishedDialog = true
                                            }
                                            ((state.typingWord.index + 1) % 20 == 0) -> {
                                                playChapterFinished()
                                                showChapterFinishedDialog = true
                                            }
                                            else -> state.typingWord.index += 1
                                        }
                                        state.saveTypingWordState()
                                    }
                                }
                            }


                            /** 检查输入的单词 */
                            val checkWordInput: (String) -> Unit = { input ->
                                wordTextFieldValue = input
                                wordTypingResult.clear()
                                var done = true
                                /**
                                 *  防止用户粘贴内容过长，如果粘贴的内容超过 word.value 的长度，
                                 * 会改变 BasicTextField 宽度，和 Text 的宽度不匹配
                                 */
                                if (wordTextFieldValue.length > currentWord.value.length) {
                                    wordTypingResult.clear()
                                    wordTextFieldValue = ""
                                } else if (input.length <= currentWord.value.length) {
                                    val chars = input.toList()
                                    for (i in chars.indices) {
                                        val inputChar = chars[i]
                                        val wordChar = currentWord.value[i]
                                        if (inputChar == wordChar) {
                                            wordTypingResult.add(Pair(inputChar, true))
                                        } else {
                                            // 字母输入错误
                                            done = false
                                            wordTypingResult.add(Pair(wordChar, false))
                                            state.speed.wrongCount = state.speed.wrongCount + 1
                                            playBeepSound()
                                            state.wordWrongTime++
                                            if (state.isDictation) {
                                                state.chapterWrongTime++
                                                val dictationWrongTime = state.dictationWrongWords[currentWord]
                                                if (dictationWrongTime != null) {
                                                    state.dictationWrongWords[currentWord] = dictationWrongTime + 1
                                                } else {
                                                    state.dictationWrongWords[currentWord] = 1
                                                }
                                            }
                                            Timer("cleanInputChar", false).schedule(50) {
                                                wordTextFieldValue = ""
                                                wordTypingResult.clear()
                                            }
                                        }
                                    }
                                    // 用户输入的单词完全正确
                                    if (wordTypingResult.size == currentWord.value.length && done) {
                                        // 输入完全正确
                                        state.speed.correctCount = state.speed.correctCount + 1
                                        playSuccessSound()
                                        if (state.isDictation) state.chapterCorrectTime++
                                        if (state.typingWord.isAuto) {
                                            Timer("cleanInputChar", false).schedule(50) {
                                                toNext()
                                                wordTextFieldValue = ""
                                                wordTypingResult.clear()
                                            }
                                        } else {
                                            state.wordCorrectTime++
                                            Timer("cleanInputChar", false).schedule(50) {
                                                wordTypingResult.clear()
                                                wordTextFieldValue = ""
                                            }
                                        }
                                    }
                                }
                            }


                            /** 检查输入的字幕 */
                            val checkCaptionsInput: (Int, String, String) -> Unit = { index, input, captionContent ->

                                if (input.length <= captionContent.length) {
//                                    captionsTextFieldValueList[index] = input
                                    when(index){
                                        0 -> captionsTextFieldValue1 = input
                                        1 -> captionsTextFieldValue2 = input
                                        2 -> captionsTextFieldValue3 = input
                                    }
                                    val typingResult = captionsTypingResultMap[index]
                                    typingResult!!.clear()
                                    val inputChars = input.toMutableList()
                                    for (i in inputChars.indices) {
                                        val inputChar = inputChars[i]
                                        val char = captionContent[i]
                                        if (inputChar == char) {
                                            typingResult.add(Pair(char, true))
                                        }else if (inputChar == ' ' && (char == '[' || char == ']')) {
                                            typingResult.add(Pair(char, true))
                                            // 音乐符号不好输入，所以可以使用空格替换
                                        }else if (inputChar == ' ' && (char == '♪')) {
                                            typingResult.add(Pair(char, true))
                                            // 音乐符号占用两个空格，所以插入♪ 再删除一个空格
                                            inputChars.add(i,'♪')
                                            inputChars.removeAt(i+1)
                                            val textFieldValue = String(inputChars.toCharArray())
                                            when(index){
                                                0 -> captionsTextFieldValue1 = textFieldValue
                                                1 -> captionsTextFieldValue2 = textFieldValue
                                                2 -> captionsTextFieldValue3 = textFieldValue
                                            }
                                        } else {
                                            typingResult.add(Pair(inputChar, false))
                                        }
                                    }
                                    if (input.length == captionContent.length) {
                                        when(index){
                                            0 -> {
                                                if(currentWord.captions.size>1){
                                                    focusRequester2.requestFocus()
                                                }
                                            }
                                            1 -> {
                                                if(currentWord.captions.size == 3){
                                                    focusRequester3.requestFocus()
                                                }
                                            }
                                        }
                                    }

                                }

                            }

                            Word(
                                state = state,
                                word = currentWord,
                                fontFamily = monospace,
                                audioPath = audioPath,
                                correctTime = state.wordCorrectTime,
                                wrongTime = state.wordWrongTime,
                                toNext = { toNext() },
                                dictationSkip = { dictationSkipCurrentWord() },
                                textFieldValue = wordTextFieldValue,
                                typingResult = wordTypingResult,
                                checkTyping = { checkWordInput(it) },
                                showChapterFinishedDialog = showChapterFinishedDialog,
                                changeShowChapterFinishedDialog = { showChapterFinishedDialog = it },
                                showEditWordDialog = showEditWordDialog,
                                setShowEditWordDialog = { showEditWordDialog = it },
                                isVocabularyFinished = isVocabularyFinished,
                                setIsVocabularyFinished = { isVocabularyFinished = it },
                                chapterCorrectTime = state.chapterCorrectTime,
                                chapterWrongTime = state.chapterWrongTime,
                                dictationWrongWords = state.dictationWrongWords,
                                resetChapterTime = { state.resetChapterTime() },
                                playKeySound = { playKeySound() },
                                jumpToCaptions = { jumpToCaptions() },
                                focusRequester = wordFocusRequester,
                            )
                            Phonetic(
                                word = currentWord,
                                phoneticVisible = state.typingWord.phoneticVisible
                            )
                            Morphology(
                                word = currentWord,
                                isPlaying = state.isPlaying,
                                morphologyVisible = state.typingWord.morphologyVisible
                            )
                            Definition(
                                word = currentWord,
                                definitionVisible = state.typingWord.definitionVisible,
                                isPlaying = state.isPlaying,
                            )
                            Translation(
                                word = currentWord,
                                translationVisible = state.typingWord.translationVisible,
                                isPlaying = state.isPlaying
                            )

                            val videoSize = videoBounds.size
                            val startPadding = if (state.isPlaying) 0.dp else 50.dp
                            val captionsModifier = Modifier
                                .fillMaxWidth()
                                .height(intrinsicSize = IntrinsicSize.Max)
                                .padding(bottom = 0.dp, start = startPadding)
                                .onPreviewKeyEvent {
                                    if ((it.key == Key.Enter || it.key == Key.NumPadEnter)
                                        && it.type == KeyEventType.KeyUp
                                    ) {
                                        toNext()
                                        true
                                    } else globalKeyEvent(it)
                                }
                            Captions(
                                captionsVisible = state.typingWord.subtitlesVisible,
                                playTripleMap = getPlayTripleMap(state, currentWord),
                                videoPlayerWindow = state.videoPlayerWindow,
                                videoPlayerComponent = state.videoPlayerComponent,
                                isPlaying = state.isPlaying,
                                volume = state.global.videoVolume,
                                setIsPlaying = { state.isPlaying = it },
                                word = currentWord,
                                bounds = videoBounds,
                                textFieldValueList = listOf(captionsTextFieldValue1,captionsTextFieldValue2,captionsTextFieldValue3),
                                typingResultMap = captionsTypingResultMap,
                                putTypingResultMap = { index, list ->
                                    captionsTypingResultMap[index] = list
                                },
                                checkTyping = { index, input, captionContent ->
                                    checkCaptionsInput(index, input, captionContent)
                                },
                                playKeySound = { playKeySound() },
                                modifier = captionsModifier,
                                focusRequesterList = listOf(focusRequester1,focusRequester2,focusRequester3),
                                jumpToWord = {jumpToWord()}
                            )
                            if (state.isPlaying) Spacer(
                                Modifier.height((videoSize.height).dp).width(videoSize.width.dp)
                            )
                        }

                    }
                } else {
                    Surface(Modifier.fillMaxSize()) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("请重新选择词库,可以拖放词库到这里", style = MaterialTheme.typography.h6)
                        }
                    }
                }
                val speedAlignment = Alignment.TopEnd
                Speed(
                    speedVisible = state.typingWord.speedVisible,
                    speed = state.speed,
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .align(speedAlignment)
                        .padding(end = endPadding)
                )
            }
        }
        Settings(
            isOpen = state.openSettings,
            setIsOpen = { state.openSettings = it },
            modifier = Modifier.align(Alignment.TopStart)
        )
    }

}

@Composable
fun MacOSTitle(
    title: String,
    window: ComposeWindow,
    modifier: Modifier
) {
    Text(
        text = title,
        color = MaterialTheme.colors.onBackground,
        modifier = modifier
    )
    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}

/**
 * 词型组件
 */
@Composable
fun Morphology(
    word: Word,
    isPlaying: Boolean,
    morphologyVisible: Boolean,
) {
    if (morphologyVisible && !isPlaying) {
        val exchanges = word.exchange.split("/")
        var preterite = ""
        var pastParticiple = ""
        var presentParticiple = ""
        var third = ""
        var er = ""
        var est = ""
        var plural = ""
        var lemma = ""

        exchanges.forEach { exchange ->
            val pair = exchange.split(":")
            when (pair[0]) {
                "p" -> {
                    preterite = pair[1]
                }
                "d" -> {
                    pastParticiple = pair[1]
                }
                "i" -> {
                    presentParticiple = pair[1]
                }
                "3" -> {
                    third = pair[1]
                }
                "r" -> {
                    er = pair[1]
                }
                "t" -> {
                    est = pair[1]
                }
                "s" -> {
                    plural = pair[1]
                }
                "0" -> {
                    lemma = pair[1]
                }
                "1" -> {
                }
            }
        }

        Column {
            SelectionContainer {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.height(IntrinsicSize.Max)
                        .width(554.dp)
                        .padding(start = 50.dp)

                ) {
                    val textColor = MaterialTheme.colors.onBackground
                    val plainStyle = SpanStyle(
                        color = textColor,
                        fontSize = 16.sp
                    )


                    Text(
                        buildAnnotatedString {
                            if (lemma.isNotEmpty()) {
                                withStyle(style = plainStyle) {
                                    append("原型 ")
                                }
                                withStyle(style = plainStyle.copy(color = Color.Magenta)) {
                                    append("$lemma")
                                }
                                withStyle(style = plainStyle) {
                                    append(";")
                                }
                            }
                            if (preterite.isNotEmpty()) {
                                var color = textColor
                                if (!preterite.endsWith("ed")) {
                                    color = if (MaterialTheme.colors.isLight) Color.Blue else Color(41, 98, 255)

                                }
                                withStyle(style = plainStyle) {
                                    append("过去式 ")
                                }
                                withStyle(style = plainStyle.copy(color = color)) {
                                    append("$preterite")
                                }
                                withStyle(style = plainStyle) {
                                    append(";")
                                }
                            }
                            if (pastParticiple.isNotEmpty()) {
                                var color = textColor
                                if (!pastParticiple.endsWith("ed")) {
                                    color =
                                        if (MaterialTheme.colors.isLight) MaterialTheme.colors.primary else Color.Yellow
                                }
                                withStyle(style = plainStyle) {
                                    append("过去分词 ")
                                }
                                withStyle(style = plainStyle.copy(color = color)) {
                                    append("$pastParticiple")
                                }
                                withStyle(style = plainStyle) {
                                    append(";")
                                }
                            }
                            if (presentParticiple.isNotEmpty()) {
                                val color = if (presentParticiple.endsWith("ing")) textColor else Color(0xFF303F9F)
                                withStyle(style = plainStyle) {
                                    append("现在分词 ")
                                }
                                withStyle(style = plainStyle.copy(color = color)) {
                                    append("$presentParticiple")
                                }
                                withStyle(style = plainStyle) {
                                    append(";")
                                }
                            }
                            if (third.isNotEmpty()) {
                                val color = if (third.endsWith("s")) textColor else Color.Cyan
                                withStyle(style = plainStyle) {
                                    append("第三人称单数 ")
                                }
                                withStyle(style = plainStyle.copy(color = color)) {
                                    append("$third")
                                }
                                withStyle(style = plainStyle) {
                                    append(";")
                                }
                            }

                            if (er.isNotEmpty()) {
                                withStyle(style = plainStyle) {
                                    append("比较级 $er;")
                                }
                            }
                            if (est.isNotEmpty()) {
                                withStyle(style = plainStyle) {
                                    append("最高级 $est;")
                                }
                            }
                            if (plural.isNotEmpty()) {
                                val color = if (plural.endsWith("s")) textColor else Color(0xFFD84315)
                                withStyle(style = plainStyle) {
                                    append("复数 ")
                                }
                                withStyle(style = plainStyle.copy(color = color)) {
                                    append("$plural")
                                }
                                withStyle(style = plainStyle) {
                                    append(";")
                                }
                            }
                        }
                    )

                }
            }
            Divider(Modifier.padding(start = 50.dp))
        }


    }

}

/**
 * 英语定义组件
 */
@Composable
fun Definition(
    word: Word,
    definitionVisible: Boolean,
    isPlaying: Boolean,
) {
    if (definitionVisible && !isPlaying) {
        val rows = word.definition.length - word.definition.replace("\n", "").length
        val normalModifier = Modifier
            .width(554.dp)
            .padding(start = 50.dp, top = 5.dp, bottom = 5.dp)
        val greaterThen10Modifier = Modifier
            .width(554.dp)
            .height(260.dp)
            .padding(start = 50.dp, top = 5.dp, bottom = 5.dp)
        Column {
            Box(modifier = if (rows > 5) greaterThen10Modifier else normalModifier) {
                val stateVertical = rememberScrollState(0)
                Box(Modifier.verticalScroll(stateVertical)) {
                    SelectionContainer {
                        Text(
                            textAlign = TextAlign.Start,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.body1.copy(lineHeight = 26.sp),
                            color = MaterialTheme.colors.onBackground,
                            modifier = Modifier.align(Alignment.CenterStart),
                            text = word.definition,
                        )
                    }
                }
                if (rows > 5) {
                    VerticalScrollbar(
                        style = LocalScrollbarStyle.current.copy(shape = if(isWindows()) RectangleShape else RoundedCornerShape(4.dp)),
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(stateVertical)
                    )
                }
            }

            Divider(Modifier.padding(start = 50.dp))
        }

    }
}

/**
 * 中文释义组件
 */
@Composable
fun Translation(
    translationVisible: Boolean,
    isPlaying: Boolean,
    word: Word
) {
    if (translationVisible && !isPlaying) {
        Column {
            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .width(554.dp)
                    .padding(start = 50.dp, top = 5.dp, bottom = 5.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = word.translation,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colors.onBackground
                    )
                }

            }
            Divider(Modifier.padding(start = 50.dp))
        }

    }
}

/** 字幕列表组件
 * @param captionsVisible 字幕的可见性
 * @param playTripleMap 要显示的字幕。Map 的类型参数说明：
 * - Map 的 Int      -> index,主要用于删除字幕，和更新时间轴
 * - Triple 的 Caption  -> caption.content 用于输入和阅读，caption.start 和 caption.end 用于播放视频
 * - Triple 的 String   -> 字幕对应的视频地址
 * - Triple 的 Int      -> 字幕的轨道
 * @param videoPlayerWindow 视频播放窗口
 * @param isPlaying 是否正在播放视频
 * @param volume 音量
 * @param setIsPlaying 设置是否正在播放视频播放的回调
 * @param word 单词
 * @param bounds 视频播放窗口的位置
 * @param textFieldValueList 用户输入的字幕列表
 * @param typingResultMap 用户输入字幕的结果 Map
 * @param putTypingResultMap 添加当前的字幕到结果Map
 * @param checkTyping 检查用户输入的回调
 * @param playKeySound 当用户输入字幕时播放敲击键盘音效的回调
 * @param modifier 修改器
 */
@ExperimentalComposeUiApi
@Composable
fun Captions(
    captionsVisible: Boolean,
    playTripleMap: Map<Int, Triple<Caption, String, Int>>,
    videoPlayerWindow: JFrame,
    videoPlayerComponent: Component,
    isPlaying: Boolean,
    volume: Float,
    setIsPlaying: (Boolean) -> Unit,
    word: Word,
    bounds: Rectangle,
    textFieldValueList: List<String>,
    typingResultMap: Map<Int, MutableList<Pair<Char, Boolean>>>,
    putTypingResultMap: (Int, MutableList<Pair<Char, Boolean>>) -> Unit,
    checkTyping: (Int, String, String) -> Unit,
    playKeySound: () -> Unit,
    modifier: Modifier,
    focusRequesterList:List<FocusRequester>,
    jumpToWord: () -> Unit,
) {
    if (captionsVisible) {
        val horizontalArrangement = if (isPlaying) Arrangement.Center else Arrangement.Start
        Row(
            horizontalArrangement = horizontalArrangement,
            modifier = modifier
        ) {
            Column {
                var plyingIndex by remember { mutableStateOf(0) }
                playTripleMap.forEach { (index, playTriple) ->
                    var captionContent = playTriple.first.content
                    if (captionContent.contains("\r\n")) {
                        captionContent = captionContent.replace("\r\n", " ")
                    } else if (captionContent.contains("\n")) {
                        captionContent = captionContent.replace("\n", " ")
                    }
                    val textFieldValue = textFieldValueList[index]
                    var typingResult = typingResultMap[index]
                    if (typingResult == null) {
                        typingResult = mutableListOf()
                        putTypingResultMap(index, typingResult)
                    }
                    Caption(
                        videoPlayerWindow = videoPlayerWindow,
                        videoPlayerComponent = videoPlayerComponent,
                        isPlaying = isPlaying,
                        setIsPlaying = {
                            setIsPlaying(it)
                        },
                        volume = volume,
                        captionContent = captionContent,
                        textFieldValue = textFieldValue,
                        typingResult = typingResult,
                        checkTyping = { editIndex, input, editContent ->
                            checkTyping(editIndex, input, editContent)
                        },
                        playKeySound = { playKeySound() },
                        index = index,
                        playingIndex = plyingIndex,
                        setPlayingIndex = {plyingIndex = it},
                        size = word.captions.size,
                        playTriple = playTriple,
                        bounds = bounds,
                        focusRequester = focusRequesterList[index],
                        jumpToWord = {jumpToWord()}
                    )
                }

            }
        }
        if (!isPlaying && (word.captions.isNotEmpty() || word.externalCaptions.isNotEmpty()))
            Divider(Modifier.padding(start = 50.dp))
    }
}

/**
 * 获取字幕
 * @return Map 的类型参数说明：
 * Int      -> index,主要用于删除字幕，和更新时间轴
 * - Triple 的 Caption  -> caption.content 用于输入和阅读，caption.start 和 caption.end 用于播放视频
 * - Triple 的 String   -> 字幕对应的视频地址
 * - Triple 的 Int      -> 字幕的轨道
 */
@OptIn(ExperimentalSerializationApi::class)
fun getPlayTripleMap(state: AppState, word: Word): MutableMap<Int, Triple<Caption, String, Int>> {

    val playTripleMap = mutableMapOf<Int, Triple<Caption, String, Int>>()
    if (state.vocabulary.type == VocabularyType.DOCUMENT) {
        if (word.externalCaptions.isNotEmpty()) {
            word.externalCaptions.forEachIndexed { index, externalCaption ->
                val caption = Caption(externalCaption.start, externalCaption.end, externalCaption.content)
                val playTriple =
                    Triple(caption, externalCaption.relateVideoPath, externalCaption.subtitlesTrackId)
                playTripleMap[index] = playTriple
            }
        }
    } else {
        if (word.captions.isNotEmpty()) {
            word.captions.forEachIndexed { index, caption ->
                val playTriple =
                    Triple(caption, state.vocabulary.relateVideoPath, state.vocabulary.subtitlesTrackId)
                playTripleMap[index] = playTriple
            }

        }
    }
    return playTripleMap
}

fun secondsToString(seconds: Double): String {
    val duration = Duration.ofMillis((seconds * 1000).toLong())
    return String.format(
        "%02d:%02d:%02d.%03d",
        duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart(), duration.toMillisPart()
    )
}

/**
 * 字幕组件
 * @param videoPlayerWindow 视频播放窗口
 * @param setIsPlaying 设置是否正在播放视频的回调
 * @param volume 音量
 * @param captionContent 字幕的内容
 * @param textFieldValue 输入的字幕
 * @param typingResult 输入字幕的结果
 * @param checkTyping 输入字幕后被调用的回调
 * @param playKeySound 当用户输入字幕时播放敲击键盘音效的回调
 * @param index 当前字幕的索引
 * @param playTriple 用于播放当前字幕的相关信息：
 * - Caption  -> caption.content 用于输入和阅读，caption.start 和 caption.end 用于播放视频
 * - String   -> 字幕对应的视频地址
 * - Int      -> 字幕的轨道
 * @param bounds 视频播放器的位置和大小
 */
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun Caption(
    videoPlayerWindow: JFrame,
    videoPlayerComponent: Component,
    isPlaying: Boolean,
    setIsPlaying: (Boolean) -> Unit,
    volume: Float,
    captionContent: String,
    textFieldValue: String,
    typingResult: List<Pair<Char, Boolean>>,
    checkTyping: (Int, String, String) -> Unit,
    playKeySound: () -> Unit,
    index: Int,
    playingIndex: Int,
    setPlayingIndex: (Int) -> Unit,
    size: Int,
    playTriple: Triple<Caption, String, Int>,
    bounds: Rectangle,
    focusRequester:FocusRequester,
    jumpToWord: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val relativeVideoPath = playTriple.second
    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(36.dp).width(IntrinsicSize.Max)
        ) {
            var selectable by remember { mutableStateOf(false) }
            val dropMenuFocusRequester = remember { FocusRequester() }
            var isFocused by remember { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            var isPathWrong by remember { mutableStateOf(false) }
            val playCurrentCaption:()-> Unit = {
                if (!isPlaying) {
                    val file = File(relativeVideoPath)
                    if (file.exists()) {
                        setIsPlaying(true)
                        scope.launch {
                            setPlayingIndex(index)
                            play(
                                videoPlayerWindow,
                                setIsPlaying = { setIsPlaying(it) },
                                volume,
                                playTriple,
                                videoPlayerComponent,
                                bounds
                            )
                        }

                    } else {
                        isPathWrong = true
                        Timer("恢复状态", false).schedule(2000) {
                            isPathWrong = false
                        }
                    }
                }
            }
            Box(Modifier.width(IntrinsicSize.Max).padding(top = 8.dp, bottom = 8.dp)) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { input ->
                        scope.launch {
                            checkTyping(index, input, captionContent)
                        }
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .align(Alignment.CenterStart)
                        .focusRequester(focusRequester)
                        .onKeyEvent {
                            when {
                                (it.type == KeyEventType.KeyDown
                                        && it.key != Key.ShiftRight
                                        && it.key != Key.ShiftLeft
                                        && it.key != Key.CtrlRight
                                        && it.key != Key.CtrlLeft
                                        ) -> {
                                    scope.launch { playKeySound() }
                                    true
                                }
                                (it.isCtrlPressed && it.key == Key.B && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch { selectable = !selectable }
                                    true
                                }
                                (it.key == Key.Tab && it.type == KeyEventType.KeyUp) -> {
                                    scope.launch {  playCurrentCaption() }
                                    true
                                }

                                (it.key == Key.DirectionDown && it.type == KeyEventType.KeyUp) -> {
                                    if(index<2 && index + 1 < size){
                                        focusManager.moveFocus(FocusDirection.Next)
                                        focusManager.moveFocus(FocusDirection.Next)
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }
                                    true
                                }

                                (it.key == Key.DirectionUp && it.type == KeyEventType.KeyUp) -> {
                                    if(index == 0){
                                        jumpToWord()
                                    }else{
                                        focusManager.moveFocus(FocusDirection.Previous)
                                        focusManager.moveFocus(FocusDirection.Previous)
                                    }

                                    true
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged {
                            isFocused = it.isFocused
                        }
                )
                Text(
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.align(Alignment.CenterStart).height(32.dp),
                    overflow = TextOverflow.Ellipsis,
                    text = buildAnnotatedString {
                        typingResult.forEach { (char, correct) ->
                            if (correct) {
                                withStyle(
                                    style = SpanStyle(
                                        color = MaterialTheme.colors.primary,
                                        fontSize = LocalTextStyle.current.fontSize,
                                        letterSpacing = LocalTextStyle.current.letterSpacing,
                                        fontFamily = LocalTextStyle.current.fontFamily,
                                    )
                                ) {
                                    append(char)
                                }
                            } else {
                                withStyle(
                                    style = SpanStyle(
                                        color = Color.Red,
                                        fontSize = LocalTextStyle.current.fontSize,
                                        letterSpacing = LocalTextStyle.current.letterSpacing,
                                        fontFamily = LocalTextStyle.current.fontFamily,
                                    )
                                ) {
                                    if (char == ' ') {
                                        append("_")
                                    } else {
                                        append(char)
                                    }

                                }
                            }
                        }
                        var remainChars = captionContent.substring(typingResult.size)
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colors.onBackground,
                                fontSize = LocalTextStyle.current.fontSize,
                                letterSpacing = LocalTextStyle.current.letterSpacing,
                                fontFamily = LocalTextStyle.current.fontFamily,
                            )
                        ) {
                            append(remainChars)
                        }
                    },
                )

                DropdownMenu(
                    expanded = selectable,
                    focusable = true,
                    onDismissRequest = {
                        selectable = false
                    },
                    offset = DpOffset(0.dp, (-30).dp)
                ) {
                    BasicTextField(
                        value = captionContent,
                        onValueChange = {},
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        textStyle =  LocalTextStyle.current.copy(
                            color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.high),
                        ),
                        modifier = Modifier.focusable()
                            .focusRequester(dropMenuFocusRequester)
                            .onKeyEvent {
                                if (it.isCtrlPressed && it.key == Key.B && it.type == KeyEventType.KeyUp) {
                                    scope.launch { selectable = !selectable }
                                    true
                                } else false
                            }
                    )
                    LaunchedEffect(Unit) {
                        dropMenuFocusRequester.requestFocus()
                    }

                }
            }

            TooltipArea(
                tooltip = {
                    Surface(
                        elevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
                        shape = RectangleShape
                    ) {
                        val ctrl = LocalCtrl.current
                        val shift = if (isMacOS()) "⇧" else "Shift"
                        val text: Any = when (index) {
                            0 -> "播放 $ctrl+$shift+Z"
                            1 -> "播放 $ctrl+$shift+X"
                            2 -> "播放 $ctrl+$shift+C"
                            else -> println("字幕数量超出范围")
                        }
                        Text(text = text.toString(), modifier = Modifier.padding(10.dp))
                    }
                },
                delayMillis = 300,
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.TopCenter,
                    alignment = Alignment.TopCenter,
                    offset = DpOffset.Zero
                )
            ) {
                IconButton(onClick = {
                    playCurrentCaption()
                }) {
                    var tint = if(isPlaying && playingIndex == index) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Localized description",
                        tint = tint
                    )
                }
            }
            if (isPathWrong) {
                Text("视频地址错误", color = Color.Red)
            }
            if(isFocused){
                TooltipArea(
                    tooltip = {
                        Surface(
                            elevation = 4.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                            ),
                            shape = RectangleShape
                        ) {
                            Row(modifier = Modifier.padding(10.dp)){
                                Text(text = "复制单词" )
                                CompositionLocalProvider(LocalContentAlpha provides 0.5f) {
                                    val ctrl = LocalCtrl.current
                                    Text(text = " $ctrl+B")
                                }
                            }

                        }
                    },
                    delayMillis = 300,
                    tooltipPlacement = TooltipPlacement.ComponentRect(
                        anchor = Alignment.TopCenter,
                        alignment = Alignment.TopCenter,
                        offset = DpOffset.Zero
                    )
                ) {
                    IconButton(onClick = { selectable = !selectable }){
                        var tint = if(selectable) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Localized description",
                            tint = tint
                        )
                    }
                }
            }
        }
    }


}


/**
 * 计算视频播放窗口的位置和大小
 */
fun computeVideoBounds(
    windowState: WindowState,
    openSettings: Boolean,
    density:Float,
): Rectangle {
    var mainX = windowState.position.x.value.toInt()
    var mainY = windowState.position.y.value.toInt()
    mainX = (mainX).div(density).toInt()
    mainY = (mainY).div(density).toInt()

    val mainWidth = windowState.size.width.value.toInt()
    val mainHeight = windowState.size.height.value.toInt()

    val size = if (mainWidth in 801..1079) {
        Dimension(642, 390)
    } else if (mainWidth > 1080) {
        Dimension(1005, 610)
    } else {
        Dimension(540, 304)
    }
    if(density!=1f){
        size.width = size.width.div(density).toInt()
        size.height = size.height.div(density).toInt()
    }
    var x = (mainWidth - size.width).div(2)
    // 232 是单词 + 字幕的高度 ，再加一个文本输入框48 == 280
    // 48 是内容的 bottom padding
    var y = ((mainHeight - 280 - size.height).div(2)) + 280 + 15-48
    x += mainX
    y += mainY
    if (openSettings) x += 109
    val point = Point(x, y)
    return Rectangle(point, size)
}

/**
 * @param currentWord 当前正在记忆的单词
 * @param index links 的 index
 * @return Triple<Caption, String, Int>? ,视频播放器需要的信息
 */
fun getPayTriple(currentWord: Word, index: Int): Triple<Caption, String, Int>? {

    return if (index < currentWord.externalCaptions.size) {
        val externalCaption = currentWord.externalCaptions[index]
        val caption = Caption(externalCaption.start, externalCaption.end, externalCaption.content)
        Triple(caption, externalCaption.relateVideoPath, externalCaption.subtitlesTrackId)
    } else {
        null
    }
}
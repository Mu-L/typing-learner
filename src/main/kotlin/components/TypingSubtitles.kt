package components

import LocalCtrl
import Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextInputService
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
import com.matthewn4444.ebml.EBMLReader
import com.matthewn4444.ebml.subtitles.SRTSubtitles
import com.matthewn4444.ebml.subtitles.SSASubtitles
import data.Caption
import dialog.removeItalicSymbol
import dialog.removeLocationInfo
import dialog.replaceNewLine
import kotlinx.coroutines.launch
import player.isMacOS
import player.isWindows
import player.mediaPlayer
import state.GlobalState
import state.TypingSubtitlesState
import state.getSettingsDirectory
import subtitleFile.FormatSRT
import subtitleFile.TimedTextObject
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.FutureTask
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.TransferHandler
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TypingSubtitles(
    typingSubtitles: TypingSubtitlesState,
    globalState: GlobalState,
    saveSubtitlesState: () -> Unit,
    saveGlobalState: () -> Unit,
    saveIsDarkTheme: (Boolean) -> Unit,
    toTypingWord: () -> Unit,
    isOpenSettings: Boolean,
    setIsOpenSettings: (Boolean) -> Unit,
    window: ComposeWindow,
    title: String,
    playerWindow: JFrame,
    videoVolume: Float,
    mediaPlayerComponent: Component,
    futureFileChooser: FutureTask<JFileChooser>,
    closeLoadingDialog: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val captionList = remember { mutableStateListOf<Caption>() }
    var isPlaying by remember { mutableStateOf(false) }
    var showOpenFile by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf("") }
    var showSelectTrack by remember { mutableStateOf(false) }
    val trackList = remember { mutableStateListOf<Pair<Int, String>>() }
    val videoPlayerBounds by remember { mutableStateOf(Rectangle(0, 0, 540, 303)) }
    val monospace by remember { mutableStateOf(FontFamily(Font("font/Inconsolata-Regular.ttf", FontWeight.Normal, FontStyle.Normal))) }
    var loading by remember { mutableStateOf(false) }

    /** 读取字幕文件*/
    if (typingSubtitles.subtitlesPath.isNotEmpty() && captionList.isEmpty()) {
        parseSubtitles(
            subtitlesPath = typingSubtitles.subtitlesPath,
            setMaxLength = {
                scope.launch {
                    typingSubtitles.sentenceMaxLength = it
                    saveSubtitlesState()
                }
            },
            setCaptionList = {
                captionList.clear()
                captionList.addAll(it)
            },
            resetSubtitlesState = {
                typingSubtitles.videoPath = ""
                typingSubtitles.subtitlesPath = ""
                typingSubtitles.trackID = 0
                typingSubtitles.trackDescription = ""
                typingSubtitles.trackSize = 0
                typingSubtitles.currentIndex = 0
                typingSubtitles.firstVisibleItemIndex = 0
                typingSubtitles.sentenceMaxLength = 0
            }
        )
    }

    /** 播放按键音效 */
    val playKeySound = {
        if (globalState.isPlayKeystrokeSound) {
            playSound("audio/keystroke.wav", globalState.keystrokeVolume)
        }
    }

    /** 设置字幕列表的被回调函数 */
    val setTrackList: (List<Pair<Int, String>>) -> Unit = {
        trackList.clear()
        trackList.addAll(it)
    }

    /** 解析打开的文件 */
    val parseImportFile: (List<File>,OpenMode) -> Unit = {files,openMode ->
        if(files.size == 1){
            val file = files.first()
            loading = true
            scope.launch {
                Thread(Runnable{
                    if (file.extension == "mkv") {
                        if (typingSubtitles.videoPath != file.absolutePath) {
                            selectedPath = file.absolutePath
                            parseTrackList(
                                mediaPlayerComponent,
                                window,
                                playerWindow,
                                file.absolutePath,
                                setTrackList = { setTrackList(it) },
                            )

                        } else {
                            JOptionPane.showMessageDialog(window, "文件已打开")
                        }

                    }else if (file.extension == "mp4") {
                        JOptionPane.showMessageDialog(window, "需要同时选择 mp4 视频 + srt 字幕")
                    }else if (file.extension == "srt") {
                        JOptionPane.showMessageDialog(window, "需要同时选择1个视频(mp4、mkv) + 1个srt 字幕")
                    }else if (file.extension == "json") {
                        JOptionPane.showMessageDialog(window, "想要打开词库文件，需要先切换到记忆单词界面")
                    } else {
                        JOptionPane.showMessageDialog(window, "格式不支持")
                    }
                    loading = false
                }).start()
            }
        }else if(files.size == 2){
            val first = files.first()
            val last = files.last()
            val modeString = if(openMode== OpenMode.Open) "打开" else "拖拽"
            if(first.extension == "srt" && (last.extension == "mp4"||last.extension == "mkv")){
                typingSubtitles.trackID = 0
                typingSubtitles.trackSize = 0
                typingSubtitles.currentIndex = 0
                typingSubtitles.firstVisibleItemIndex = 0
                typingSubtitles.subtitlesPath = first.absolutePath
                typingSubtitles.videoPath = last.absolutePath
                typingSubtitles.trackDescription = first.nameWithoutExtension
                captionList.clear()
                if(openMode == OpenMode.Open) showOpenFile = false
            }else if((first.extension == "mp4"||first.extension == "mkv") && last.extension == "srt"){
                typingSubtitles.trackID = 0
                typingSubtitles.trackSize = 0
                typingSubtitles.currentIndex = 0
                typingSubtitles.firstVisibleItemIndex = 0
                typingSubtitles.videoPath = first.absolutePath
                typingSubtitles.subtitlesPath = last.absolutePath
                typingSubtitles.trackDescription = last.nameWithoutExtension
                captionList.clear()
                if(openMode == OpenMode.Open) showOpenFile = false
            }else if(first.extension == "mp4" && last.extension == "mp4"){
                JOptionPane.showMessageDialog(window, "${modeString}了2个 MP4 格式的视频，\n需要1个视频（mp4、mkv）和1个 srt 字幕")
            }else if(first.extension == "mkv" && last.extension == "mkv"){
                JOptionPane.showMessageDialog(window, "${modeString}了2个 MKV 格式的视频，\n"
                        +"可以选择一个有字幕的 mkv 格式的视频，\n或者一个 MKV 格式的视频和1个 srt 字幕")
            }else if(first.extension == "mkv" && last.extension == "mp4"){
                JOptionPane.showMessageDialog(window, "${modeString}了2个视频，\n需要1个视频（mp4、mkv）和1个 srt 字幕")
            }else if(first.extension == "mp4" && last.extension == "mkv"){
                JOptionPane.showMessageDialog(window, "${modeString}了2个视频，\n需要1个视频（mp4、mkv）和1个 srt 字幕")
            }else if(first.extension == "srt" && last.extension == "srt"){
                JOptionPane.showMessageDialog(window, "${modeString}了2个字幕，\n需要1个视频（mp4、mkv）和1个 srt 字幕")
            }else {
                JOptionPane.showMessageDialog(window, "文件格式不支持")
            }
        }else{
            JOptionPane.showMessageDialog(window, "不能超过两个文件")
        }

    }
    /** 打开文件对话框 */
    val openFileChooser: () -> Unit = {
        val fileChooser = futureFileChooser.get()
        fileChooser.dialogTitle = "打开"
        fileChooser.fileSystemView = FileSystemView.getFileSystemView()
        fileChooser.currentDirectory = FileSystemView.getFileSystemView().defaultDirectory
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.isAcceptAllFileFilterUsed = false
        fileChooser.isMultiSelectionEnabled = true
        val fileFilter = FileNameExtensionFilter("1个 mkv 视频，或 1个视频(mp4、mkv) + 1个字幕(srt)","mkv","srt","mp4")
        fileChooser.addChoosableFileFilter(fileFilter)
        fileChooser.selectedFile = null
        if (fileChooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
            val files = fileChooser.selectedFiles.toList()
            parseImportFile(files,OpenMode.Open)
            closeLoadingDialog()
        } else {
            closeLoadingDialog()
        }
        fileChooser.selectedFile = null
        fileChooser.isMultiSelectionEnabled = false
        fileChooser.removeChoosableFileFilter(fileFilter)
    }

    /**  使用按钮播放视频时调用的回调函数   */
    val buttonEventPlay: (Caption) -> Unit = { caption ->
        val file = File(typingSubtitles.videoPath)
        if (file.exists()) {
            if (!isPlaying) {
                scope.launch {
                    isPlaying = true
                    val playTriple = Triple(caption, typingSubtitles.videoPath, typingSubtitles.trackID)
                    play(
                        window = playerWindow,
                        setIsPlaying = { isPlaying = it },
                        volume = videoVolume,
                        playTriple = playTriple,
                        videoPlayerComponent = mediaPlayerComponent,
                        bounds = videoPlayerBounds
                    )
                }
            }

        } else {
            JOptionPane.showMessageDialog(window, "视频地址错误")
        }

    }

    /** 保存轨道 ID 时被调用的回调函数 */
    val saveTrackID: (Int) -> Unit = {
        scope.launch {
            typingSubtitles.trackID = it
            saveSubtitlesState()
        }
    }

    /** 保存轨道名称时被调用的回调函数 */
    val saveTrackDescription: (String) -> Unit = {
        scope.launch {
            typingSubtitles.trackDescription = it
            saveSubtitlesState()
        }
    }

    /** 保存轨道数量时被调用的回调函数 */
    val saveTrackSize: (Int) -> Unit = {
        scope.launch {
            typingSubtitles.trackSize = it
            saveSubtitlesState()
        }
    }

    /** 保存视频路径时被调用的回调函数 */
    val saveVideoPath: (String) -> Unit = {
        typingSubtitles.videoPath = it
        saveSubtitlesState()
    }

    /** 保存一个新的字幕时被调用的回调函数 */
    val saveSubtitlesPath: (String) -> Unit = {
        scope.launch {
            typingSubtitles.subtitlesPath = it
            typingSubtitles.firstVisibleItemIndex = 0
            typingSubtitles.currentIndex = 0
            focusManager.clearFocus()
            /** 把之前的字幕列表清除才能触发解析字幕的函数重新运行 */
            captionList.clear()
            saveSubtitlesState()
        }
    }

    /** 保存是否启用击键音效时被调用的回调函数 */
    val saveIsPlayKeystrokeSound: (Boolean) -> Unit = {
        scope.launch {
            globalState.isPlayKeystrokeSound = it
            saveGlobalState()
        }
    }

    val selectTypingSubTitles:() -> Unit = {
        if (trackList.isEmpty()) {
            loading = true
            scope.launch {
                showSelectTrack = true
                Thread(Runnable{
                    parseTrackList(
                        mediaPlayerComponent,
                        window,
                        playerWindow,
                        typingSubtitles.videoPath,
                        setTrackList = {
                            setTrackList(it)
                        },
                    )
                    loading = false

                }).start()

            }

        }
    }
    /** 当前界面的快捷键 */
    val boxKeyEvent: (KeyEvent) -> Boolean = { keyEvent ->
        when {
            (keyEvent.isCtrlPressed && keyEvent.key == Key.T && keyEvent.type == KeyEventType.KeyUp) -> {
                toTypingWord()
                true
            }
            (keyEvent.isCtrlPressed && keyEvent.key == Key.O && keyEvent.type == KeyEventType.KeyUp) -> {
                openFileChooser()
                showOpenFile = true
                true
            }
            (keyEvent.isCtrlPressed && keyEvent.key == Key.S && keyEvent.type == KeyEventType.KeyUp) -> {
                selectTypingSubTitles()
                true
            }
            (keyEvent.isCtrlPressed && keyEvent.key == Key.D && keyEvent.type == KeyEventType.KeyUp) -> {
                saveIsDarkTheme(!globalState.isDarkTheme)
                true
            }
            (keyEvent.isCtrlPressed && keyEvent.key == Key.M && keyEvent.type == KeyEventType.KeyUp) -> {
                saveIsPlayKeystrokeSound(!globalState.isPlayKeystrokeSound)
                true
            }
            ((keyEvent.key == Key.Tab) && keyEvent.type == KeyEventType.KeyUp) -> {
                val caption = captionList[typingSubtitles.currentIndex]
                val playTriple = Triple(caption, typingSubtitles.videoPath, typingSubtitles.trackID)
                if (!isPlaying) {
                    scope.launch {
                        isPlaying = true
                        play(
                            window = playerWindow,
                            setIsPlaying = { isPlaying = it },
                            volume = videoVolume,
                            playTriple = playTriple,
                            videoPlayerComponent = mediaPlayerComponent,
                            bounds = videoPlayerBounds
                        )
                    }

                }
                true
            }
            else -> false
        }
    }


    /**  处理拖放文件的函数 */
    val transferHandler = createTransferHandler(
        singleFile = false,
        showWrongMessage = { message ->
            JOptionPane.showMessageDialog(window, message)
        },
        parseImportFile = { parseImportFile(it,OpenMode.Drag) }
    )

    window.transferHandler = transferHandler
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colors.background)
            .focusRequester(focusRequester)
            .onKeyEvent(boxKeyEvent)
            .focusable()
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        Row(Modifier.fillMaxSize()) {
            SubtitlesSidebar(
                isOpen = isOpenSettings,
                back = { toTypingWord() },
                trackSize = typingSubtitles.trackSize,
                openFile = { showOpenFile = true },
                openFileChooser = { openFileChooser() },
                selectTrack = { selectTypingSubTitles() },
                isDarkTheme = globalState.isDarkTheme,
                setIsDarkTheme = { saveIsDarkTheme(it) },
                isPlayKeystrokeSound = globalState.isPlayKeystrokeSound,
                setIsPlayKeystrokeSound = { saveIsPlayKeystrokeSound(it) },
            )
            val topPadding = if (isMacOS()) 30.dp else 0.dp
            if (isOpenSettings) {
                Divider(Modifier.fillMaxHeight().width(1.dp).padding(top = topPadding))
            }
            Box(Modifier.fillMaxSize().padding(top = topPadding)) {

                if (captionList.isNotEmpty()) {

                    val listState = rememberLazyListState(typingSubtitles.firstVisibleItemIndex)
                    val stateHorizontal = rememberScrollState(0)
                    val isAtTop by remember {
                        derivedStateOf {
                            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        }
                    }
                    val startTimeWidth = 50.dp
                    val endPadding = 10.dp
                    val maxWidth = startTimeWidth + endPadding + (typingSubtitles.sentenceMaxLength * 13).dp
                    val indexWidth = (captionList.size.toString().length * 14).dp
                    LazyColumn(
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(1050.dp)
                            .fillMaxHeight()
                            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)
                            .horizontalScroll(stateHorizontal),
                    ) {
                        itemsIndexed(captionList) { index, caption ->
                            val captionContent = caption.content
                            val typingResult = remember { mutableStateListOf<Pair<Char, Boolean>>() }
                            var textFieldValue by remember { mutableStateOf("") }
                            val next :() -> Unit = {
                                scope.launch {
                                    val end =
                                        listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size - 2
                                    if (index >= end) {
                                        listState.scrollToItem(index)
                                    }
                                    focusManager.moveFocus(FocusDirection.Next)
                                    focusManager.moveFocus(FocusDirection.Next)
                                    focusManager.moveFocus(FocusDirection.Next)
                                }
                            }
                            val previous :() -> Unit = {
                                scope.launch {
                                    if(index == listState.firstVisibleItemIndex+1){
                                        val top = index - listState.layoutInfo.visibleItemsInfo.size
                                        listState.scrollToItem(top)
                                        typingSubtitles.currentIndex = index-1

                                    }else{
                                        focusManager.moveFocus(FocusDirection.Previous)
                                        focusManager.moveFocus(FocusDirection.Previous)
                                    }

                                }
                            }
                            /** 检查输入的回调函数 */
                            val checkTyping: (String) -> Unit = { input ->
                                scope.launch {
                                    if (textFieldValue.length > captionContent.length) {
                                        typingResult.clear()
                                        textFieldValue = ""

                                    } else if (input.length <= captionContent.length) {
                                        textFieldValue = input
                                        typingResult.clear()
                                        val inputChars = input.toList()
                                        for (i in inputChars.indices) {
                                            val inputChar = inputChars[i]
                                            val char = captionContent[i]
                                            if (inputChar == char) {
                                                typingResult.add(Pair(inputChar, true))
                                                // 方括号的语义很弱，又不好输入，所以可以使用空格替换
                                            } else if (inputChar == ' ' && (char == '[' || char == ']')) {
                                                typingResult.add(Pair(char, true))
                                            } else {
                                                typingResult.add(Pair(inputChar, false))
                                            }
                                        }
                                        if(input.length >= captionContent.length){
                                            next()
                                        }

                                    }else{
                                        next()
                                    }
                                }
                            }

                            val textFieldKeyEvent: (KeyEvent) -> Boolean = { it: KeyEvent ->
                                when {
                                    ((it.key != Key.ShiftLeft && it.key != Key.ShiftRight) && it.type == KeyEventType.KeyDown) -> {
                                        playKeySound()
                                        true
                                    }
                                    ((it.key == Key.Enter ||it.key == Key.NumPadEnter || it.key == Key.DirectionDown) && it.type == KeyEventType.KeyUp) -> {
                                        next()
                                        true
                                    }

                                    ((it.key == Key.DirectionUp) && it.type == KeyEventType.KeyUp) -> {
                                        previous()
                                        true
                                    }
                                    ((it.key == Key.DirectionLeft) && it.type == KeyEventType.KeyUp) -> {
                                        scope.launch {
                                            val current = stateHorizontal.value
                                            stateHorizontal.scrollTo(current-20)
                                        }
                                        true
                                    }
                                    ((it.key == Key.DirectionRight) && it.type == KeyEventType.KeyUp) -> {
                                        scope.launch {
                                            val current = stateHorizontal.value
                                            stateHorizontal.scrollTo(current+20)
                                        }
                                        true
                                    }
                                    ((it.key == Key.Backspace) && it.type == KeyEventType.KeyUp) -> {
                                        scope.launch {
                                            if(textFieldValue.isEmpty()){
                                                previous()
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }

                            }
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .width(maxWidth)
                                    .padding(start = 150.dp)
                            ) {
                                val alpha = if(typingSubtitles.currentIndex == index) ContentAlpha.high else ContentAlpha.medium
                                val lineColor =  if(index <  typingSubtitles.currentIndex){
                                    MaterialTheme.colors.primary.copy(alpha = ContentAlpha.medium)
                                }else{
                                    MaterialTheme.colors.onBackground.copy(alpha = alpha)
                                }
                                val indexColor =  if(index <=  typingSubtitles.currentIndex){
                                    MaterialTheme.colors.primary.copy(alpha = ContentAlpha.medium)
                                }else{
                                    MaterialTheme.colors.onBackground.copy(alpha = alpha)
                                }

                                Row(modifier = Modifier.width(indexWidth)){
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(
                                                style = SpanStyle(
                                                    color = indexColor,
                                                    fontSize = MaterialTheme.typography.h5.fontSize,
                                                    letterSpacing = MaterialTheme.typography.h5.letterSpacing,
                                                    fontFamily = MaterialTheme.typography.h5.fontFamily,
                                                )
                                            ) {
                                                append("${index+1}")
                                            }
                                        },
                                    )
                                }

                                Spacer(Modifier.width(20.dp))
                                Box(Modifier.width(IntrinsicSize.Max)) {
                                    CompositionLocalProvider(
                                        LocalTextInputService provides null
                                    ) {


                                        BasicTextField(
                                            value = textFieldValue,
                                            onValueChange = { checkTyping(it) },
                                            singleLine = true,
                                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
                                            textStyle = MaterialTheme.typography.h5.copy(
                                                color = Color.Transparent,
                                                fontFamily = monospace
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .align(Alignment.CenterStart)
                                                .focusable()
                                                .onKeyEvent { textFieldKeyEvent(it) }
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        scope.launch {
                                                            typingSubtitles.currentIndex = index
                                                            typingSubtitles.firstVisibleItemIndex =
                                                                listState.firstVisibleItemIndex
                                                            saveSubtitlesState()
                                                        }
                                                    } else if (textFieldValue.isNotEmpty()) {
                                                        typingResult.clear()
                                                        textFieldValue = ""
                                                    }
                                                }
                                        )

                                    }
                                    Text(
                                        text = buildAnnotatedString {

                                            typingResult.forEach { (char, correct) ->
                                                if (correct) {
                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = MaterialTheme.colors.primary.copy(alpha = alpha),
                                                            fontSize = MaterialTheme.typography.h5.fontSize,
                                                            letterSpacing = MaterialTheme.typography.h5.letterSpacing,
                                                            fontFamily = monospace,
                                                        )
                                                    ) {
                                                        append(char)
                                                    }
                                                } else {
                                                    withStyle(
                                                        style = SpanStyle(
                                                            color = Color.Red,
                                                            fontSize = MaterialTheme.typography.h5.fontSize,
                                                            letterSpacing = MaterialTheme.typography.h5.letterSpacing,
                                                            fontFamily = monospace,
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
                                                    color = lineColor,
                                                    fontSize = MaterialTheme.typography.h5.fontSize,
                                                    letterSpacing = MaterialTheme.typography.h5.letterSpacing,
                                                    fontFamily = monospace,
                                                )
                                            ) {
                                                append(remainChars)
                                            }
                                        },
                                        textAlign = TextAlign.Start,
                                        color = MaterialTheme.colors.onBackground,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                    )

                                    if (typingSubtitles.currentIndex == index) {
                                        Divider(
                                            Modifier.align(Alignment.BottomCenter)
                                                .background(MaterialTheme.colors.primary)
                                        )
                                    }
                                }

                                Row(Modifier.width(48.dp).height(IntrinsicSize.Max)) {
                                    if (typingSubtitles.currentIndex == index) {
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
                                                        Text(text = "播放" )
                                                        CompositionLocalProvider(LocalContentAlpha provides 0.5f) {
                                                            Text(text = " Tab")
                                                        }
                                                    }

                                                }
                                            },
                                            delayMillis = 300,
                                            tooltipPlacement = TooltipPlacement.ComponentRect(
                                                anchor = Alignment.CenterEnd,
                                                alignment = Alignment.CenterEnd,
                                                offset = DpOffset.Zero
                                            )
                                        ) {
                                            val density = LocalDensity.current.density
                                            IconButton(onClick = {
                                                buttonEventPlay(caption)
                                            },
                                                modifier = Modifier
                                                    .onKeyEvent {
                                                        if (it.key == Key.Spacebar && it.type == KeyEventType.KeyUp) {
                                                            buttonEventPlay(caption)
                                                            true
                                                        } else false
                                                    }
                                                    .onGloballyPositioned { coordinates ->
                                                        val rect = coordinates.boundsInWindow()
                                                        videoPlayerBounds.x = window.x + rect.left.toInt() + (48 * density).toInt()
                                                        videoPlayerBounds.y = window.y + rect.top.toInt() - (100 * density).toInt()

                                                        // 判断屏幕边界
                                                        val graphicsDevice =
                                                            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice

                                                        val width = graphicsDevice.displayMode.width
                                                        val height = graphicsDevice.displayMode.height
                                                        val actualWidth = (540 * density).toInt()
                                                        if (videoPlayerBounds.x + actualWidth > width) {
                                                            videoPlayerBounds.x = width - actualWidth
                                                        }
                                                        val actualHeight = (330 * density).toInt()
                                                        if (videoPlayerBounds.y < 0) videoPlayerBounds.y = 0
                                                        if (videoPlayerBounds.y + actualHeight > height) {
                                                            videoPlayerBounds.y = height - actualHeight
                                                        }

                                                        // 显示器缩放
                                                        if(density != 1f){
                                                            videoPlayerBounds.x = videoPlayerBounds.x.div(density).toInt()
                                                            videoPlayerBounds.y =  videoPlayerBounds.y.div(density).toInt()
                                                        }
                                                    }
                                            ) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = "Localized description",
                                                    tint = MaterialTheme.colors.primary
                                                )
                                            }

                                        }

                                    }
                                }

                            }

                        }
                    }

                    VerticalScrollbar(
                        style = LocalScrollbarStyle.current.copy(shape = if(isWindows()) RectangleShape else RoundedCornerShape(4.dp)),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState = listState)
                    )
                    HorizontalScrollbar(
                        style = LocalScrollbarStyle.current.copy(shape = if(isWindows()) RectangleShape else RoundedCornerShape(4.dp)),
                        modifier = Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        adapter = rememberScrollbarAdapter(stateHorizontal)
                    )
                    if (!isAtTop) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.scrollToItem(0)
                                    typingSubtitles.currentIndex = 0
                                    typingSubtitles.firstVisibleItemIndex = 0
                                    focusManager.clearFocus()
                                    saveSubtitlesState()
                                }
                            },
                            backgroundColor = if (MaterialTheme.colors.isLight) Color.LightGray else Color.DarkGray,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 100.dp, bottom = 100.dp)
                        ) {
                            Icon(
                                Icons.Filled.North,
                                contentDescription = "Localized description",
                                tint = MaterialTheme.colors.primary
                            )
                        }
                    }
                }

                if (showOpenFile || selectedPath.isNotEmpty() || captionList.isEmpty()) {
                    OpenFileComponent(
                        parentComponent = window,
                        cancel = { showOpenFile = false },
                        openFileChooser = { openFileChooser() },
                        showCancel = captionList.isNotEmpty(),
                        setTrackId = { saveTrackID(it) },
                        setTrackDescription = { saveTrackDescription(it) },
                        trackList = trackList,
                        setTrackList = { setTrackList(it) },
                        setVideoPath = { saveVideoPath(it) },
                        selectedPath = selectedPath,
                        setSelectedPath = { selectedPath = it },
                        setSubtitlesPath = { saveSubtitlesPath(it) },
                        setTrackSize = { saveTrackSize(it) },
                    )
                }
                if (showSelectTrack) {
                    Box(
                        Modifier.fillMaxSize()
                            .align(Alignment.Center)
                            .background(MaterialTheme.colors.background)
                    ) {
                        Row(Modifier.align(Alignment.Center)) {
                            SelectTrack(
                                close = { showSelectTrack = false },
                                parentComponent = window,
                                setTrackId = { saveTrackID(it) },
                                setTrackDescription = { saveTrackDescription(it) },
                                trackList = trackList,
                                setTrackList = { setTrackList(it) },
                                setVideoPath = { saveVideoPath(it) },
                                selectedPath = typingSubtitles.videoPath,
                                setSelectedPath = { selectedPath = it },
                                setSubtitlesPath = { saveSubtitlesPath(it) },
                                setTrackSize = { saveTrackSize(it) },
                                setIsLoading = { loading = it }
                            )
                            OutlinedButton(onClick = { showSelectTrack = false }) {
                                Text("取消")
                            }
                        }

                    }
                }
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.width(60.dp).align(Alignment.Center).padding(bottom = 200.dp)
                    )
                }
            }

        }

        if (isMacOS()) {
            MacOSTitle(
                title = title,
                window = window,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
            )
        }
        Settings(
            isOpen = isOpenSettings,
            setIsOpen = { setIsOpenSettings(it) },
            modifier = Modifier.align(Alignment.TopStart)
        )

    }

}

enum class OpenMode {
    Open, Drag,
}
@Composable
fun OpenFileComponent(
    parentComponent: Component,
    cancel: () -> Unit,
    openFileChooser: () -> Unit,
    showCancel: Boolean,
    setTrackId: (Int) -> Unit,
    setTrackDescription: (String) -> Unit,
    trackList: List<Pair<Int, String>>,
    setTrackList: (List<Pair<Int, String>>) -> Unit,
    setVideoPath: (String) -> Unit,
    selectedPath: String,
    setSelectedPath: (String) -> Unit,
    setSubtitlesPath: (String) -> Unit,
    setTrackSize: (Int) -> Unit,
) {

    Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        var loading by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(IntrinsicSize.Max).align(Alignment.Center)
        ) {

            Text(
                text = "可以拖放 MKV 文件到这里",
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(end = 20.dp)
            )
            OutlinedButton(
                modifier = Modifier.padding(end = 20.dp),
                onClick = { openFileChooser() }) {
                Text("打开")
            }

            SelectTrack(
                close = { cancel() },
                parentComponent = parentComponent,
                setTrackId = { setTrackId(it) },
                setTrackDescription = { setTrackDescription(it) },
                trackList = trackList,
                setTrackList = { setTrackList(it) },
                setVideoPath = { setVideoPath(it) },
                selectedPath = selectedPath,
                setSelectedPath = { setSelectedPath(it) },
                setSubtitlesPath = { setSubtitlesPath(it) },
                setTrackSize = { setTrackSize(it) },
                setIsLoading = { loading = it }
            )
            if (showCancel) {
                OutlinedButton(onClick = {
                    setTrackList(listOf())
                    setSelectedPath("")
                    cancel()
                }) {
                    Text("取消")
                }
            }
        }
        if (loading) {
            CircularProgressIndicator(Modifier.width(60.dp).align(Alignment.Center).padding(bottom = 200.dp))
        }
    }

}

@Composable
fun SelectTrack(
    close: () -> Unit,
    parentComponent: Component,
    setTrackId: (Int) -> Unit,
    setTrackDescription: (String) -> Unit,
    trackList: List<Pair<Int, String>>,
    setTrackList: (List<Pair<Int, String>>) -> Unit,
    setVideoPath: (String) -> Unit,
    selectedPath: String,
    setSelectedPath: (String) -> Unit,
    setSubtitlesPath: (String) -> Unit,
    setTrackSize: (Int) -> Unit,
    setIsLoading: (Boolean) -> Unit,
) {
    if (trackList.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        var selectedSubtitle by remember { mutableStateOf("    ") }
        Box(Modifier.width(IntrinsicSize.Max).padding(end = 20.dp)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .width(282.dp)
                    .background(Color.Transparent)
                    .border(1.dp, Color.Transparent)
            ) {
                Text(
                    text = selectedSubtitle, fontSize = 12.sp,
                )
                Icon(
                    Icons.Default.ExpandMore, contentDescription = "Localized description",
                    modifier = Modifier.size(20.dp, 20.dp)
                )
            }
            val dropdownMenuHeight = (trackList.size * 40 + 20).dp
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(282.dp)
                    .height(dropdownMenuHeight)
            ) {
                trackList.forEach { (trackId, description) ->
                    DropdownMenuItem(
                        onClick = {
                            setIsLoading(true)
                            Thread(Runnable {
                                expanded = false
                                val subtitles = writeToFile(selectedPath, trackId,parentComponent)
                                if (subtitles != null) {
                                    setSubtitlesPath(subtitles.absolutePath)
                                    setTrackId(trackId)
                                    setTrackDescription(description)
                                    setVideoPath(selectedPath)

                                    setTrackSize(trackList.size)
                                    setTrackList(listOf())
                                    setSelectedPath("")
                                    close()
                                }
                                setIsLoading(false)

                            }).start()
                        },
                        modifier = Modifier.width(282.dp).height(40.dp)
                    ) {
                        Text(
                            text = "$description ", fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun Settings(
    modifier: Modifier
) {
    Icon(
        Icons.Filled.ArrowBack,
        contentDescription = "Localized description",
        tint = MaterialTheme.colors.primary,
        modifier = modifier,
    )
}

@Composable
fun SubtitlesSidebar(
    isOpen: Boolean,
    isDarkTheme: Boolean,
    setIsDarkTheme: (Boolean) -> Unit,
    isPlayKeystrokeSound: Boolean,
    setIsPlayKeystrokeSound: (Boolean) -> Unit,
    trackSize: Int,
    back: () -> Unit,
    openFile: () -> Unit,
    openFileChooser: () -> Unit,
    selectTrack: () -> Unit,
) {
    if (isOpen) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .width(216.dp)
                .fillMaxHeight()
        ) {
            Spacer(Modifier.fillMaxWidth().height(if (isMacOS()) 78.dp else 48.dp))
            Divider()
            val ctrl = LocalCtrl.current

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(48.dp).clickable { back() }.padding(start = 16.dp, end = 8.dp)
            ) {
                Row {
                    Text("记忆单词", color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$ctrl+T",
                        color = MaterialTheme.colors.onBackground
                    )
                }
                Spacer(Modifier.width(15.dp))
                Icon(
                    Icons.Filled.TextFields,
                    contentDescription = "Localized description",
                    tint = if (MaterialTheme.colors.isLight) Color.DarkGray else MaterialTheme.colors.onBackground,
                    modifier = Modifier.size(48.dp, 48.dp).padding(top = 12.dp, bottom = 12.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        openFileChooser()
                        openFile()
                    }
                    .fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp)
            ) {
                Row {
                    Text("打开文件", color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$ctrl+O",
                        color = MaterialTheme.colors.onBackground
                    )
                }
                Spacer(Modifier.width(15.dp))
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = "Localized description",
                    tint = if (MaterialTheme.colors.isLight) Color.DarkGray else MaterialTheme.colors.onBackground,
                    modifier = Modifier.size(48.dp, 48.dp).padding(top = 12.dp, bottom = 12.dp)
                )
            }
            if (trackSize > 1) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { selectTrack() }
                        .fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp)
                ) {
                    Row {
                        Text("选择字幕", color = MaterialTheme.colors.onBackground)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$ctrl+S",
                            color = MaterialTheme.colors.onBackground
                        )
                    }
                    Spacer(Modifier.width(15.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Localized description",
                        tint = if (MaterialTheme.colors.isLight) Color.DarkGray else MaterialTheme.colors.onBackground,
                        modifier = Modifier.size(48.dp, 48.dp).padding(top = 12.dp, bottom = 12.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { }.padding(start = 16.dp, end = 8.dp)
            ) {
                Row {
                    Text("深色模式", color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$ctrl+D",
                        color = MaterialTheme.colors.onBackground
                    )
                }

                Spacer(Modifier.width(15.dp))
                Switch(
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary),
                    checked = isDarkTheme,
                    onCheckedChange = { setIsDarkTheme(it) },
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable { }.padding(start = 16.dp, end = 8.dp)
            ) {
                Row {
                    Text("击键音效", color = MaterialTheme.colors.onBackground)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$ctrl+M",
                        color = MaterialTheme.colors.onBackground
                    )
                }

                Spacer(Modifier.width(15.dp))
                Switch(
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary),
                    checked = isPlayKeystrokeSound,
                    onCheckedChange = { setIsPlayKeystrokeSound(it) },
                )
            }

        }
    }
}

/** 创建拖放处理器
 * @param singleFile 是否只接收单个文件
 * @param parseImportFile 处理导入的文件的函数
 * @param showWrongMessage 显示提示信息的函数
 */
fun createTransferHandler(
    singleFile: Boolean = true,
    parseImportFile: (List<File>) -> Unit,
    showWrongMessage: (String) -> Unit,
): TransferHandler {
    return object : TransferHandler() {
        override fun canImport(support: TransferSupport): Boolean {
            if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                return false
            }
            return true
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) {
                return false
            }
            val transferable = support.transferable
            try {
                val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                if (singleFile) {
                    if (files.size == 1) {
                        parseImportFile(files)
                    } else {
                        showWrongMessage("一次只能读取一个文件")
                    }
                } else {
                    parseImportFile(files)
                }


            } catch (exception: UnsupportedFlavorException) {
                return false
            } catch (exception: IOException) {
                return false
            }
            return true
        }
    }
}

/**
 * 解析选择的文件，返回字幕名称列表，用于用户选择具体的字幕。
 * @param mediaPlayerComponent VLC 组件
 * @param playerWindow 播放视频的窗口
 * @param videoPath 视频路径
 * @param setTrackList 解析完成后，用来设置字幕列表的回调。
 */
fun parseTrackList(
    mediaPlayerComponent: Component,
    parentComponent: Component,
    playerWindow: JFrame,
    videoPath: String,
    setTrackList: (List<Pair<Int, String>>) -> Unit,
) {
    val result = checkSubtitles(videoPath,parentComponent)
    if(result){
        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                val list = mutableListOf<Pair<Int, String>>()
                mediaPlayer.subpictures().trackDescriptions().forEachIndexed { index, trackDescription ->
                    if (index != 0) {
                        list.add(Pair(index - 1, trackDescription.description()))
                    }
                }
                mediaPlayer.controls().pause()
                playerWindow.isAlwaysOnTop = true
                playerWindow.title = "视频播放窗口"
                playerWindow.isVisible = false
                setTrackList(list)
                mediaPlayerComponent.mediaPlayer().events().removeMediaPlayerEventListener(this)
            }
        })
        playerWindow.title = "正在读取字幕列表"
        playerWindow.isAlwaysOnTop = false
        playerWindow.toBack()
        playerWindow.size = Dimension(10, 10)
        playerWindow.location = Point(0, 0)
        playerWindow.layout = null
        playerWindow.contentPane.add(mediaPlayerComponent)
        playerWindow.isVisible = true
        mediaPlayerComponent.mediaPlayer().media().play(videoPath)
    }
}

/**
 * 有些文件，可能文件扩展是mkv,但实际内容并不是 mkv
 */
fun checkSubtitles(
    videoPath: String,
    parentComponent: Component):Boolean{
    var reader: EBMLReader? = null

    try {
        reader = EBMLReader(videoPath)
        /**
         * Check to see if this is a valid MKV file
         * The header contains information for where all the segments are located
         */
        if (!reader.readHeader()) {
            JOptionPane.showMessageDialog(parentComponent, "这不是一个 mkv 格式的视频")
            return false
        }

        /**
         * Read the tracks. This contains the details of video, audio and subtitles
         * in this file
         */
        reader.readTracks()

        /**
         * Check if there are any subtitles in this file
         */
        val numSubtitles: Int = reader.subtitles.size
        if (numSubtitles == 0) {
            JOptionPane.showMessageDialog(parentComponent, "这个视频没有字幕")
            return false
        }
    } catch (exception: IOException) {
        exception.printStackTrace()
    } finally {
        try {
            reader?.close()
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }
    return true
}

/**
 * 解析字幕，返回最大字符数和字幕列表，用于显示。
 * @param subtitlesPath 字幕的路径
 * @param setMaxLength 用于设置字幕的最大字符数的回调函数
 * @param setCaptionList 用于设置字幕列表的回调函数
 * @param resetSubtitlesState 字幕文件删除，或者被修改，导致不能解析，就重置
 */
fun parseSubtitles(
    subtitlesPath: String,
    setMaxLength: (Int) -> Unit,
    setCaptionList: (List<Caption>) -> Unit,
    resetSubtitlesState:() -> Unit,
) {
    val formatSRT = FormatSRT()
    val file = File(subtitlesPath)
    if(file.exists()){
        try {
            val inputStream: InputStream = FileInputStream(file)
            val timedTextObject: TimedTextObject = formatSRT.parseFile(file.name, inputStream)
            val captions: TreeMap<Int, subtitleFile.Caption> = timedTextObject.captions
            val captionList = mutableListOf<Caption>()
            var maxLength = 0
            for (caption in captions.values) {
                var content = removeLocationInfo(caption.content)
                content = removeItalicSymbol(content)
                content = replaceNewLine(content)

                val newCaption = Caption(
                    start = caption.start.getTime("hh:mm:ss.ms"),
                    end = caption.end.getTime("hh:mm:ss.ms"),
                    content = content
                )
                if (caption.content.length > maxLength) {
                    maxLength = caption.content.length
                }
                captionList.add(newCaption)
            }

            setMaxLength(maxLength)
            setCaptionList(captionList)
        }catch (exception:IOException){
            exception.printStackTrace()
            resetSubtitlesState()
        }

    }else{
        println("找不到正在抄写的字幕")
        resetSubtitlesState()
    }

}

/**
 * 提取选择的字幕到用户目录
 * */
private fun writeToFile(
    videoPath: String,
    trackId: Int,
    parentComponent: Component,
): File? {
    var reader: EBMLReader? = null
    val settingsDir = getSettingsDirectory()
    var subtitlesFile: File? = null

    try {
        reader = EBMLReader(videoPath)
        /**
         * Check to see if this is a valid MKV file
         * The header contains information for where all the segments are located
         */
        if (!reader.readHeader()) {
            println("This is not an mkv file!")
            return subtitlesFile
        }

        /**
         * Read the tracks. This contains the details of video, audio and subtitles
         * in this file
         */
        reader.readTracks()

        /**
         * Check if there are any subtitles in this file
         */
        val numSubtitles: Int = reader.subtitles.size
        if (numSubtitles == 0) {
            return subtitlesFile
        }

        /**
         * You need this to find the clusters scattered across the file to find
         * video, audio and subtitle data
         */
        reader.readCues()

        /**
         *  Read all the subtitles from the file each from cue index.
         *  Once a cue is parsed, it is cached, so if you read the same cue again,
         *  it will not waste time.
         *  Performance-wise, this will take some time because it needs to read
         *  most of the file.
         */
        for (i in 0 until reader.cuesCount) {
            reader.readSubtitlesInCueFrame(i)
        }

        val subtitles = reader.subtitles[trackId]
        if(subtitles is SSASubtitles){
            JOptionPane.showMessageDialog(parentComponent, "暂时不支持 ASS 格式的字幕")
        }else if(subtitles is SRTSubtitles){
            subtitlesFile = File(settingsDir, "subtitles.srt")
            subtitles.writeFile(subtitlesFile.absolutePath)
        }

    } catch (exception: Exception) {
        exception.printStackTrace()
    } finally {
        try {
            reader?.close()
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }
    return subtitlesFile
}
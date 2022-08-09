package dialog

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import data.Word
import kotlinx.serialization.ExperimentalSerializationApi
import state.AppState
import state.MemoryStrategy

/**
 * 选择章节
 */
@OptIn(
    ExperimentalSerializationApi::class
)
@ExperimentalComposeUiApi
@Composable
fun SelectChapterDialog(
    close:() -> Unit,
    state: AppState,
    isMultiple:Boolean
) {
    Dialog(
        title = if(isMultiple) "听写复习" else "选择章节",
        onCloseRequest = { close() },
        resizable = true,
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(930.dp, 785.dp)
        ),
    ) {
        Surface(
            elevation = 5.dp,
            shape = RectangleShape,
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colors.background)
            ) {
                val checkedChapters  = remember{
                    if(isMultiple){
                        mutableStateListOf()
                    }else{
                        mutableStateListOf(state.typingWord.chapter)
                    }
                }

                val selectedWords = remember(checkedChapters){
                    derivedStateOf {
                        val list = mutableStateListOf<Word>()
                        checkedChapters.forEach { chapter ->
                            val start = chapter * 20 - 20
                            var end = chapter * 20
                            if(end > state.vocabulary.wordList.size){
                                end = state.vocabulary.wordList.size
                            }
                            val subList = state.vocabulary.wordList.subList(start, end)
                            list.addAll(subList)
                        }
                        list
                    }
                }

                if(isMultiple){
                    Column(modifier = Modifier.align(Alignment.TopCenter)) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp)
                        ) {
                            Text("选择了${checkedChapters.size}个章节  ", color = MaterialTheme.colors.onBackground)
                            Text("${selectedWords.value.size}", color = MaterialTheme.colors.primary)
                            Text(" 个单词", color = MaterialTheme.colors.onBackground)
                        }
                        Divider()
                    }
                }


                Row(modifier = Modifier.align(Alignment.Center).padding(top = 33.dp, bottom = 55.dp)) {
                    Chapters(
                        checkedChapters = checkedChapters,
                        size = state.vocabulary.size,
                        isMultiple = isMultiple,
                        onChapterSelected = {
                            if(!isMultiple){
                                checkedChapters.clear()
                                checkedChapters.add(it)
                            }else{
                                if(checkedChapters.contains(it)){
                                    checkedChapters.remove(it)
                                }else{
                                    checkedChapters.add(it)
                                }
                            }
                        },
                    )
                }
                Footer(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    confirm = {
                        if(isMultiple){
                            if(state.memoryStrategy != MemoryStrategy.Review && state.memoryStrategy != MemoryStrategy.Dictation){
                                state.hiddenInfo()
                            }

                            state.memoryStrategy = MemoryStrategy.Review
                            state.wrongWords.clear()
                            state.reviewWords.clear()
                            state.reviewWords.addAll(selectedWords.value.shuffled())
                            state.dictationIndex = 0

                        }else{
                            val chapter = checkedChapters.first()
                            if (chapter == 0) state.typingWord.chapter = 1
                            state.typingWord.chapter = chapter
                            state.typingWord.index = (chapter - 1) * 20
                            state.saveTypingWordState()
                        }
                            close()

                    },
                    exit = { close() })
            }
        }
    }
}



@Composable
fun Chapters(
    size: Int,
    checkedChapters: List<Int>,
    onChapterSelected: (Int) -> Unit,
    isMultiple:Boolean
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colors.background)
    ) {
        var count = size / 20
        val mod = size % 20
        if (mod != 0 && size > 20) count += 1
        if (size < 20) count = 1
        val chapters = (1 until count + 1).map { "Chapter $it" }.toList()
        val listState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(144.dp),
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier.fillMaxWidth(),
            state = listState
        ) {
            itemsIndexed(chapters) { index: Int, item: String ->
                val chapter = index + 1
                var checkedState = if(isMultiple){
                     checkedChapters.contains(chapter)
                }else{
                    chapter == checkedChapters.first()
                }

                val onClick:() -> Unit = {
                    if(isMultiple){
                        onChapterSelected(chapter)
                    }else{
                        // 如果已经选择了这个章节，就取消选择这个章节，章节设置为0，
                        // 否则设置选择的章节
                        onChapterSelected(if (checkedState) 0 else chapter)
                    }

                }

                Card(
                    modifier = Modifier
                        .padding(7.5.dp)
                        .clickable {
                            onClick()
                        },
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 3.dp
                ) {
                    Box(Modifier.size(width = 144.dp, height = 65.dp)) {
                        Text(
                            text = item,
                            color = MaterialTheme.colors.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                        )

                        val words = if (index == count - 1){
                            if(mod==0) 20 else mod
                        } else 20
                        Text(
                            text = "$words 词",
                            color = MaterialTheme.colors.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                        )
                        if(!isMultiple){
                            if (checkedState) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { onClick()},
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                )
                            }
                        }else{
                            Checkbox(
                                checked = checkedState,
                                onCheckedChange = {  onClick()},
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }

                    }
                }
            }

        }
// 相关 Issue: https://github.com/JetBrains/compose-jb/issues/2029
//        VerticalScrollbar(
//            style = LocalScrollbarStyle.current.copy(shape = if(isWindows()) RectangleShape else RoundedCornerShape(4.dp)),
//            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
//            adapter = rememberScrollbarAdapter(
//                scrollState = listState
//            )
//        )

    }
}

@Composable
fun Footer(modifier: Modifier, confirm: () -> Unit, exit: () -> Unit) {
    Box(modifier = modifier) {
        Column {
            Divider(Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.background)
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(top = 10.dp, bottom = 10.dp)
            ) {
                OutlinedButton(onClick = { confirm() }) {
                    Text(text = "确认", color = MaterialTheme.colors.onBackground)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { exit() }) {
                    Text(text = "取消", color = MaterialTheme.colors.onBackground)
                }
                Spacer(Modifier.width(10.dp))
            }
        }
    }


}
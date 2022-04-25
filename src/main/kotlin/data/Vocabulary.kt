package data

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.ResourceLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import state.getResourcesFile
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.swing.JOptionPane
import kotlin.Exception

/**
 * 词库
 */
@Serializable
data class Vocabulary(
    var name: String = "",
    val type: VocabularyType = VocabularyType.DOCUMENT,
    val language: String,
    var size: Int,
    val relateVideoPath: String = "",
    val subtitlesTrackId: Int = 0,
    var wordList: MutableList<Word> = mutableListOf(),
)

/**
 * 可观察的词库
 */
class MutableVocabulary(vocabulary: Vocabulary) {
    var name by mutableStateOf(vocabulary.name)
    var type by mutableStateOf(vocabulary.type)
    var language by mutableStateOf(vocabulary.language)
    var size by mutableStateOf(vocabulary.size)
    var relateVideoPath by mutableStateOf(vocabulary.relateVideoPath)
    var subtitlesTrackId by mutableStateOf(vocabulary.subtitlesTrackId)
    var wordList = getMutableStateList(vocabulary.wordList)

    /**
     * 用于持久化
     */
    val serializeVocabulary
        get() = Vocabulary(name, type, language, size, relateVideoPath, subtitlesTrackId, wordList)

}

/**
 * 获得可观察的单词列表
 */
fun getMutableStateList(wordList: MutableList<Word>): MutableList<Word> {
    val list = mutableStateListOf<Word>()
    list.addAll(wordList)
    return list
}
/**
 links 存储字幕链接,格式：(subtitlePath)[videoPath][subtitleTrackId][index]
 captions 字幕列表
 */
@Serializable
data class Word(
    var value: String,
    var usphone: String = "",
    var ukphone: String = "",
    var definition: String = "",
    var translation: String = "",
    var pos: String = "",
    var collins: Int = 0,
    var oxford: Boolean = false,
    var tag: String = "",
    var bnc: Int? = 0,
    var frq: Int? = 0,
    var exchange: String = "",
    var externalCaptions: MutableList<ExternalCaption> = mutableListOf(),
    var captions: MutableList<Caption> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        val otherWord = other as Word
        return this.value == otherWord.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}



@Serializable
data class Caption(var start: String, var end: String, var content: String) {
    override fun toString(): String {
        return content
    }
}

/**
 * @param relateVideoPath 视频地址
 * @param subtitlesTrackId 字幕轨道 ID
 * @param subtitlesName 字幕名称，链接字幕词库时设置。在链接字幕窗口，用来删除这个字幕文件的所有字幕
 * @param start 开始
 * @param end 结束
 * @param content 字幕内容
 */
@Serializable
data class ExternalCaption(val relateVideoPath: String,val subtitlesTrackId: Int,var subtitlesName: String,var start: String,var end: String,var content: String){
    override fun toString(): String {
        return content
    }
}


fun loadMutableVocabulary(path: String):MutableVocabulary{
    val file = getResourcesFile(path)
    return if (file.exists()) {

        try {
         val vocabulary = Json.decodeFromString<Vocabulary>(file.readText())
            MutableVocabulary(vocabulary)
        }catch (exception:Exception){
            exception.printStackTrace()
           val vocabulary = Vocabulary(
                name = "",
                type = VocabularyType.DOCUMENT,
                language = "",
                size = 0,
                relateVideoPath = "",
                subtitlesTrackId = 0,
                wordList = mutableListOf()
            )
            JOptionPane.showMessageDialog(null,"词库解析错误：\n地址：$path\n"+exception.message)
            MutableVocabulary(vocabulary)
        }

    }else{
        val vocabulary = Vocabulary(
            name = "",
            type = VocabularyType.DOCUMENT,
            language = "",
            size = 0,
            relateVideoPath = "",
            subtitlesTrackId = 0,
            wordList = mutableListOf()
        )
        JOptionPane.showMessageDialog(null,"找不到词库：\n$path")
        MutableVocabulary(vocabulary)
    }

}




@OptIn(ExperimentalComposeUiApi::class)
fun loadVocabulary(path: String): Vocabulary {
    val file = getResourcesFile(path)
    if(file.exists()){
        return try{
            Json.decodeFromString(file.readText())
        } catch (exception: Exception) {
            JOptionPane.showMessageDialog(null,"词库解析错误：\n地址：$path\n"+exception.message)
            Vocabulary(
                name = "",
                type = VocabularyType.DOCUMENT,
                language = "",
                size = 0,
                relateVideoPath = "",
                subtitlesTrackId = 0,
                wordList = mutableListOf()
            )
        }
    }else{

        return Vocabulary(
            name = "",
            type = VocabularyType.DOCUMENT,
            language = "",
            size = 0,
            relateVideoPath = "",
            subtitlesTrackId = 0,
            wordList = mutableListOf()
        )
    }

}


fun saveVocabularyToTempDirectory(vocabulary: Vocabulary, directory: String) {
    val format = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    val json = format.encodeToString(vocabulary)
    val file = File("src/main/resources/temp/$directory/${vocabulary.name}.json")
    if (!file.parentFile.exists()) {
        file.parentFile.mkdirs()
    }
    File("src/main/resources/temp/$directory/${vocabulary.name}.json").writeText(json)
}

fun saveVocabulary(vocabulary: Vocabulary, path: String) {
    val format = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    Thread(Runnable {
        val json = format.encodeToString(vocabulary)
        val file = getResourcesFile(path)
        file.writeText(json)
    }).start()
}

fun main() {
    
}

/**
 * 主要用于批量转换词库，
 * 调用方式：convertWord(File("D:\\qwerty-learner-desktop\\resources\\common\\vocabulary"))
 */
fun convertWord(dir: File) {

    dir.listFiles().forEach { file ->

        if (file.isDirectory) {
            convertWord(file)
        } else {

            val oldVocabulary = Json.decodeFromString<OldVocabulary>(file.readText())
            var newList = mutableListOf<Word>()

            for (oldWord in oldVocabulary.wordList) {
                val word = Word(
                    value = oldWord.value,
                    usphone = oldWord.usphone,
                    ukphone = oldWord.ukphone,
                    definition = oldWord.definition,
                    translation = oldWord.translation,
                    pos = oldWord.pos,
                    collins = oldWord.collins,
                    oxford = oldWord.oxford,
                    tag = oldWord.tag,
                    bnc = oldWord.bnc,
                    frq = oldWord.frq,
                    exchange = oldWord.exchange,
                    externalCaptions = oldWord.links,
                    captions = oldWord.captions
                )
                newList.add(word)
            }


            val vocabulary = Vocabulary(
                name = file.nameWithoutExtension,
                language = oldVocabulary.language,
                size = oldVocabulary.size,
                wordList = newList
            )

            val directory = file.parent.split("\\").last()
            saveVocabularyToTempDirectory(vocabulary, directory)
        }
    }
}

@Serializable
data class OldWord(
    var value: String,
    var usphone: String = "",
    var ukphone: String = "",
    var definition: String = "",
    var translation: String = "",
    var pos: String = "",
    var collins: Int = 0,
    var oxford: Boolean = false,
    var tag: String = "",
    var bnc: Int? = 0,
    var frq: Int? = 0,
    var exchange: String = "",
    var links: MutableList<ExternalCaption> = mutableListOf(),
    var captions: MutableList<Caption> = mutableListOf()
)

@Serializable
data class OldVocabulary(
    var name: String = "",
    val type: VocabularyType = VocabularyType.DOCUMENT,
    val language: String,
    var size: Int,
    val relateVideoPath: String = "",
    val subtitlesTrackId: Int = 0,
    var wordList: MutableList<OldWord> = mutableListOf(),
)

fun loadOldVocabulary(pathname: String): OldVocabulary {
    return Json.decodeFromString(File(pathname).readText())
}

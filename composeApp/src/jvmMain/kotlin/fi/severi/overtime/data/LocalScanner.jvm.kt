package fi.severi.overtime.data

import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

private val ART_NAMES = listOf("poster", "folder", "cover")
private val ART_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")

/** Finds video files under [folders] (any depth), with artwork next to them. Unreadable folders are skipped. */
actual fun scanLocalFolders(folders: List<String>): List<LocalFile> {
    val result = ArrayList<LocalFile>()
    for (root in folders.map(::File).filter { it.isDirectory }) {
        root.walkTopDown()
            .onEnter { !it.isHidden || it == root }
            .filter { it.isDirectory }
            .forEach { dir ->
                val videos = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }.orEmpty()
                if (videos.isEmpty()) return@forEach
                val folderArt = findArt(dir, ART_NAMES)
                val parentArt = dir.parentFile?.takeIf { dir != root }?.let { findArt(it, ART_NAMES) }
                val relative = dir.relativeTo(root).invariantSeparatorsPath.split('/').filter { it.isNotEmpty() }
                val path = listOf(root.name) + relative
                for (video in videos) {
                    result += LocalFile(
                        path = video.absolutePath,
                        name = video.name,
                        folders = path,
                        ownArt = findArt(dir, listOf(video.nameWithoutExtension, "${video.nameWithoutExtension}-poster")),
                        folderArt = folderArt,
                        parentArt = parentArt,
                        videosInFolder = videos.size,
                    )
                }
            }
    }
    return result
}

private fun findArt(dir: File, names: List<String>): String? {
    for (name in names) for (ext in ART_EXTENSIONS) {
        val file = File(dir, "$name.$ext")
        if (file.isFile) return file.toURI().toString()
    }
    return null
}

/** A system folder picker; null when cancelled. */
actual fun pickFolder(): String? {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val chooser = JFileChooser().apply {
        dialogTitle = "Add a folder with movies or series"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}

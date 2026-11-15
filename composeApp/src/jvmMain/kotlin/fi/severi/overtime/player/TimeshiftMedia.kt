package fi.severi.overtime.player

import uk.co.caprica.vlcj.media.callback.DefaultCallbackMedia

/**
 * Feeds libVLC from a [TimeshiftRecorder], starting at [startOffset] and following the recording.
 * Not seekable from VLC's side: seeking is done by starting a new instance at another offset, which
 * keeps VLC from probing the (unbounded) stream end.
 */
class TimeshiftMedia(
    private val recorder: TimeshiftRecorder,
    startOffset: Long,
    private val onFellBehind: () -> Unit,
) : DefaultCallbackMedia(false) {

    @Volatile var offset: Long = startOffset
        private set

    @Volatile private var cancelled = false

    /** Makes any blocked read return so VLC can stop this input promptly. */
    fun cancel() {
        cancelled = true
    }

    /** Unknown size (UINT64_MAX), as for any live stream. */
    override fun onGetSize(): Long = -1L

    override fun onOpen(): Boolean = !cancelled

    override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
        while (!cancelled) {
            when (val n = recorder.read(offset, buffer, bufferSize, POLL_MS)) {
                TimeshiftRecorder.END -> return 0
                TimeshiftRecorder.FELL_BEHIND -> {
                    // Paused longer than the buffer: the data under us was dropped.
                    offset = recorder.earliestOffset
                    onFellBehind()
                }
                0 -> Unit // waiting at the live edge
                else -> {
                    offset += n
                    return n
                }
            }
        }
        return 0
    }

    override fun onSeek(offset: Long): Boolean = false

    override fun onClose() {
        cancelled = true
    }

    private companion object {
        const val POLL_MS = 250L
    }
}

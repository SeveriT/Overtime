package fi.severi.overtime.player

import uk.co.caprica.vlcj.media.callback.DefaultCallbackMedia

/**
 * Feeds libVLC from a [TimeshiftRecorder], starting at [startOffset] and following the recording.
 *
 * Must be *seekable*: VLC 3's callback input (modules/access/imem-access.c) only reports
 * STREAM_CAN_PAUSE / STREAM_CAN_CONTROL_PACE when a seek callback exists. Without it VLC treats the
 * input as an unpaced live source, reads everything up to the live edge as fast as it can and resyncs
 * its clock there — a rewind would jump back to live after a few seconds, and pause would not hold.
 *
 * VLC sees positions relative to [startOffset]; the size stays unknown so it never probes the end.
 * Large jumps are still done by starting a new instance (see VlcPlayer.seekTo).
 */
class TimeshiftMedia(
    private val recorder: TimeshiftRecorder,
    private val startOffset: Long,
    private val onFellBehind: () -> Unit,
) : DefaultCallbackMedia(true) {

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

    /** VLC's small seeks (probing, resync) within what is buffered. */
    override fun onSeek(offset: Long): Boolean {
        val target = startOffset + offset
        if (target < recorder.earliestOffset || target > recorder.latestOffset) return false
        this.offset = target
        return true
    }

    override fun onClose() {
        cancelled = true
    }

    private companion object {
        const val POLL_MS = 250L
    }
}

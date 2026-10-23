package com.music.bitchord.playback

import android.content.Context
import androidx.media3.session.MediaController
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.DownloadTarget
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.MediaTagger
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import com.music.bitchord.playback.cast.CastController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Every way the interface starts, reorders or saves music, in one place.
 *
 * This is BitChord's own play/queue/radio/download behaviour, lifted out of
 * the composable that used to hold it so any interface can drive it: the
 * party guard, the bounded album/playlist context, the one-off station for a
 * song picked on its own, the user-queue insertion point ahead of AutoPlay,
 * and the release bookkeeping behind a batch download.
 */
class PlaybackActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val controller: () -> MediaController?,
    private val currentQueue: () -> List<Song>,
    /** Short confirmations ("Added to queue"), shown however the UI likes. */
    private val notify: (String) -> Unit,
) {
    private var playRequestGeneration = 0
    private val queueLabel = "Queue"

    /**
     * Whether the host has taken the music, and say so if they have. Every
     * action that starts or reorders playback asks this first.
     */
    fun refusedByHost(): Boolean {
        val locked = ListenTogether.state.value.controlsLocked
        if (locked) notify("Only the host can change the music")
        return locked
    }

    fun togglePlayPause() {
        val c = controller() ?: return
        val party = ListenTogether.state.value
        when {
            party.controlsLocked && !party.playback.isPlaying && !c.isPlaying ->
                notify("Only the host can change the music")
            c.isPlaying -> c.pause()
            else -> c.play()
        }
    }

    private fun timelineOf(c: MediaController): List<Song> =
        currentQueue().takeIf { it.size == c.mediaItemCount }
            ?: (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }

    /** Play [allSongs] from [allIndex] as the context it was shown in. */
    fun playFrom(
        allSongs: List<Song>,
        allIndex: Int,
        source: QueueSource,
        boundedContext: Boolean = false,
        continuationToken: String? = null,
    ) {
        if (allSongs.getOrNull(allIndex)?.isUnresolvedSpotify == true) return
        val songs = allSongs.filterNot { it.isUnresolvedSpotify }
        val index = songs.indexOf(allSongs.getOrNull(allIndex)).coerceAtLeast(0)
        playRequestGeneration++
        scope.launch {
            if (refusedByHost()) return@launch
            val c = controller() ?: return@launch
            val currentTimeline = timelineOf(c)
            val currentIndex = c.currentMediaItemIndex
            if (ListenTogether.state.value.inParty) {
                val selectedSong = songs.getOrNull(index) ?: return@launch
                val party = ListenTogether.state.value
                val partyIndex = com.music.bitchord.data.listentogether.partyQueueIndexOf(
                    party.queue, party.playback, party.playback.track?.videoId,
                )
                val upcoming = if (partyIndex >= 0) party.queue.items.drop(partyIndex + 1) else emptyList()
                val timeline = QueueCoordinator.buildPartyPlaybackQueue(
                    tappedSong = selectedSong,
                    source = source,
                    upcomingPartyTracks = upcoming,
                )
                c.playSongs(timeline, 0)
            } else if (boundedContext && !CastController.state.value.casting) {
                c.playBoundedContext(songs, index, source, continuationToken)
            } else {
                val result = QueueCoordinator.buildContextQueue(
                    currentTimeline = currentTimeline,
                    currentIndex = currentIndex,
                    newContextSongs = songs,
                    selectedIndex = index,
                    contextSource = source,
                )
                c.playSongs(result.timeline, result.startIndex)
            }
        }
    }

    /**
     * Shuffled start of a list. Shuffle goes on first so the queue is built
     * shuffled as it is set; the random pick only decides which track leads.
     */
    fun shuffle(songs: List<Song>, source: QueueSource, boundedContext: Boolean = false) {
        if (songs.isEmpty() || refusedByHost()) return
        QueueShuffle.enableForNextQueue()
        playFrom(songs, songs.indices.random(), source, boundedContext)
    }

    /**
     * A row tapped on an album or playlist page. The page snapshot is handed to
     * the service as a bounded context, which then owns continuation loading;
     * [takePlaybackContext] cancels the page's fill-in and returns its newest
     * complete snapshot.
     */
    fun playDetail(
        page: DetailPage,
        songs: List<Song>,
        index: Int,
        takePlaybackContext: (String) -> DetailPage?,
        shuffle: Boolean = false,
    ) {
        if (refusedByHost()) return
        if (shuffle) QueueShuffle.enableForNextQueue()
        val withAlbum: (Song) -> Song = { song ->
            if (page.type == BrowseType.ALBUM) song.copy(albumName = song.albumName ?: page.title) else song
        }
        val source = QueueSource(page.title, PlaybackSourceType.BROWSE, page.browseId)
        val selected = songs.getOrNull(index)
        val pageSongKeys = (page.songs as? UiState.Success)?.data.orEmpty().mapTo(HashSet()) { it.contextEntryKey() }
        val useBounded = (page.type == BrowseType.ALBUM || page.type == BrowseType.PLAYLIST) &&
            selected?.contextEntryKey()?.let(pageSongKeys::contains) == true &&
            !CastController.state.value.casting
        val latestPage = if (useBounded) takePlaybackContext(page.browseId) else null
        val latestSongs = latestPage
            ?.let { (it.songs as? UiState.Success)?.data.orEmpty() }
            ?.map(withAlbum)
            ?.takeIf { it.isNotEmpty() }
        val queue = latestSongs ?: songs.map(withAlbum)
        if (queue.isEmpty()) return
        val queueIndex = selected?.let { tapped ->
            queue.indexOfFirst { it.contextEntryKey() == tapped.contextEntryKey() }
        }?.takeIf { it >= 0 } ?: index
        playFrom(
            queue,
            queueIndex.coerceIn(queue.indices),
            source,
            boundedContext = useBounded,
            continuationToken = latestPage?.continuation ?: page.continuation,
        )
    }

    /**
     * A song picked on its own (a home card, a search hit) starts a station
     * rather than queueing the list it was shown in.
     */
    fun playRadio(song: Song, source: QueueSource) {
        playRequestGeneration++
        scope.launch {
            if (refusedByHost()) return@launch
            val c = controller() ?: return@launch
            val oneOffQueue = QueueCoordinator.buildOneOffQueue(
                currentTimeline = timelineOf(c),
                currentIndex = c.currentMediaItemIndex,
                tappedSong = song,
                source = source,
            )
            c.playSongs(oneOffQueue, 0)
        }
    }

    /** "Start radio" from a song menu: replaces the queue with its station. */
    fun startRadio(song: Song) {
        val original = controller() ?: return
        if (refusedByHost()) return
        val request = ++playRequestGeneration
        val originalManualQueue = (0 until original.mediaItemCount)
            .map { original.getMediaItemAt(it) }
            .filterNot { it.fromAutoplay }
            .map { it.mediaId }
        scope.launch {
            val seed = song.copy(
                radioName = song.title,
                playbackSource = song.title,
                playbackSourceType = PlaybackSourceType.SHARED_LINK,
                playbackSourceId = song.videoId,
            )
            val related = loadAutoplayTracks(listOf(seed), seed, INITIAL_RADIO_TRACKS).getOrNull().orEmpty()
            if (related.isEmpty()) {
                if (request == playRequestGeneration) notify("Couldn't load tracks")
                return@launch
            }
            val active = controller() ?: return@launch
            val activeManualQueue = (0 until active.mediaItemCount)
                .map { active.getMediaItemAt(it) }
                .filterNot { it.fromAutoplay }
                .map { it.mediaId }
            if (request != playRequestGeneration || activeManualQueue != originalManualQueue) return@launch
            active.beginRadioQueue()
            val currentIndex = active.currentMediaItemIndex
            if (currentIndex >= 0 && active.currentMediaItem?.mediaId == song.videoId) {
                if (currentIndex + 1 < active.mediaItemCount) active.removeMediaItems(currentIndex + 1, active.mediaItemCount)
                if (currentIndex > 0) active.removeMediaItems(0, currentIndex)
                active.addMediaItems(1, related.map { it.toMediaItem() })
            } else {
                active.playSongs(listOf(seed) + related, 0)
            }
            active.commitRadioQueue()
            notify("Radio started from ${song.title}")
        }
    }

    fun playNext(song: Song) = queueSongs(listOf(song), next = true)

    fun addToQueue(song: Song) = queueSongs(listOf(song), next = false)

    /**
     * A list onto the queue — straight after the current track, or behind
     * everything else the user queued but still ahead of AutoPlay.
     */
    fun queueSongs(songs: List<Song>, next: Boolean) {
        if (songs.isEmpty()) return
        scope.launch {
            if (refusedByHost()) return@launch
            val c = controller()
            if (c == null || c.mediaItemCount == 0) {
                val first = songs.first()
                playFrom(
                    songs, 0,
                    QueueSource(first.playbackSource ?: first.albumName ?: queueLabel, PlaybackSourceType.QUEUE),
                )
                return@launch
            }
            val toAdd = if (ListenTogether.state.value.inParty) {
                val upcoming = (c.mediaItemCount - (c.currentMediaItemIndex + 1)).coerceAtLeast(0)
                val slotsLeft = (PARTY_QUEUE_LIMIT - upcoming).coerceAtLeast(0)
                if (slotsLeft <= 0) {
                    notify("The party queue is full ($PARTY_QUEUE_LIMIT songs)")
                    return@launch
                }
                songs.take(slotsLeft)
            } else {
                songs
            }
            val at = QueueCoordinator.findUserQueueInsertionIndex(
                timeline = timelineOf(c),
                currentIndex = c.currentMediaItemIndex,
                isNext = next,
            )
            val current = c.currentMediaItem?.toSong()
            c.addMediaItems(
                at,
                toAdd.map {
                    it.copy(
                        radioName = current?.radioName,
                        playbackSource = current?.playbackSource ?: queueLabel,
                        playbackSourceType = current?.playbackSourceType ?: PlaybackSourceType.QUEUE,
                        playbackSourceId = current?.playbackSourceId,
                    ).asQueueEntry(QueueTier.USER_QUEUE).toMediaItem()
                },
            )
            notify(
                when {
                    toAdd.size == 1 && next -> "Playing next"
                    toAdd.size == 1 -> "Added to queue"
                    next -> "${toAdd.size} songs will play next"
                    else -> "${toAdd.size} songs added to queue"
                },
            )
        }
    }

    private var switchingVersion = false

    /**
     * Swaps the playing track between its official audio and its music video
     * (the audio of it — the player never shows the picture). The current
     * item is never removed up front, so a failed resolve just resumes it.
     */
    fun switchVersion(song: Song, toVideo: Boolean) {
        val c = controller() ?: return
        if (refusedByHost() || switchingVersion) return
        val index = c.currentMediaItemIndex
        if (index !in 0 until c.mediaItemCount || c.currentMediaItem?.mediaId != song.videoId) return
        scope.launch {
            switchingVersion = true
            val holdUntilAligned = AppSettings.smartVersionAlignment.value
            if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = true
            try {
                val other = runCatching {
                    if (toVideo) YtMusicRepository.resolveVideo(song) else YtMusicRepository.resolveAudio(song)
                }.getOrNull()
                val stillCurrent = c.currentMediaItemIndex == index && c.currentMediaItem?.mediaId == song.videoId
                if (other == null || other.videoId == song.videoId) {
                    if (stillCurrent) notify(if (toVideo) "No video for this song" else "No song version found")
                    return@launch
                }
                if (!stillCurrent) return@launch
                val target = other.copy(
                    isVideoOrigin = if (toVideo) other.isVideoOrigin else true,
                    queueTier = song.queueTier,
                    queueEntryId = song.queueEntryId,
                    radioName = song.radioName,
                    playbackSource = song.playbackSource,
                    playbackSourceType = song.playbackSourceType,
                    playbackSourceId = song.playbackSourceId,
                )
                if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = false
                c.swapToVersion(target)
            } finally {
                if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = false
                switchingVersion = false
            }
        }
    }

    /** Jump to an entry already in the queue. */
    fun skipToQueueIndex(index: Int) {
        val c = controller() ?: return
        if (refusedByHost()) return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller() ?: return
        if (refusedByHost()) return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) {
        val c = controller() ?: return
        if (refusedByHost()) return
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) c.removeMediaItem(index)
    }

    /** "Clear" on Up next: drops everything after the current track. */
    fun clearUpcoming() {
        val c = controller() ?: return
        if (refusedByHost()) return
        val next = c.currentMediaItemIndex + 1
        if (next < c.mediaItemCount) c.removeMediaItems(next, c.mediaItemCount)
    }

    /**
     * Downloads [requested]; when it is a whole release, [from] records it so
     * the Downloads page can group the tracks back into the album or playlist.
     * Returns false when downloads aren't allowed right now (e.g. Wi-Fi only).
     */
    fun download(requested: List<Song>, from: DownloadTarget? = null): Boolean {
        val saved = Downloads.saved.value
        val songs = requested
            .filter { it.videoId !in saved }
            .map { song ->
                val cover = from?.thumbnailUrl
                if (song.thumbnailUrl.isNullOrBlank() && !cover.isNullOrBlank()) song.copy(thumbnailUrl = cover) else song
            }
        val blocked = songs.isNotEmpty() && !AppSettings.downloadsAllowedNow
        if (songs.isNotEmpty() && !blocked) {
            songs.forEach { Downloads.enqueue(context, it, from?.title) }
            if (from != null) Downloads.markRequested(from.id, songs.map { it.videoId })
        }
        if (from != null && !blocked) {
            Downloads.rememberCollection(from, requested)
            if (!from.thumbnailUrl.isNullOrBlank()) {
                scope.launch(Dispatchers.IO) {
                    MediaTagger.cacheArtwork(context.applicationContext, from.thumbnailUrl)
                        ?.let { Downloads.rememberCollectionArtwork(from.id, it) }
                }
            }
        }
        when {
            blocked -> notify("Downloads are set to Wi-Fi only")
            songs.size > 1 -> notify("Downloading ${songs.size} songs")
            songs.size == 1 && requested.size == 1 -> notify("Downloading")
            songs.isEmpty() && requested.isNotEmpty() -> notify("Already downloaded")
        }
        return !blocked
    }

    fun removeDownload(videoId: String) {
        scope.launch { Downloads.delete(context, videoId) }
    }

    companion object {
        private const val INITIAL_RADIO_TRACKS = 24
        private const val PARTY_QUEUE_LIMIT = 25
    }
}

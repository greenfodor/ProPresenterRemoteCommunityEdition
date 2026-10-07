package com.greenfodor.ppremotece.core.domain.audio

import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioNode
import com.greenfodor.ppremotece.core.domain.model.Transport
import kotlinx.coroutines.flow.StateFlow

/**
 * The connected host's audio bin from the status stream: [audioPlaylists], its tree of folders and
 * playlists, is [Loadable.NotLoaded] until the first frame and while disconnected;
 * [audioPlaylistsRepeats] counts the `audio/playlists` frames that were the first after the stream
 * returned from reconnecting and carried the tree already held; [activeAudio] is the audio playlist track that
 * plays, null without one; [audioTransport] is what the audio layer has loaded,
 * not loaded until its first frame and unavailable when ProPresenter rejected its subscription, and
 * [audioPosition] how far into it the audio is, in seconds, null until known; the position is
 * null again from the moment other audio is loaded until its first position arrives.
 */
interface AudioRepository {
    val audioPlaylists: StateFlow<Loadable<List<AudioNode>>>
    val audioPlaylistsRepeats: StateFlow<Int>
    val activeAudio: StateFlow<ActiveAudio?>
    val audioTransport: StateFlow<Loadable<Transport>>
    val audioPosition: StateFlow<Double?>
}

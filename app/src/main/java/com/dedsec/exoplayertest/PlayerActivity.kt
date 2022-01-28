package com.dedsec.exoplayertest

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.dedsec.exoplayertest.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util


class PlayerActivity : Activity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var binding : ActivityPlayerBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }


    private fun initializePlayer() {

        val drmCallback = HttpMediaDrmCallback(LICENCE_URL, DefaultHttpDataSource.Factory())

        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setMultiSession(false)
            .build(drmCallback)

        val mediaDataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)

        val mediaSource =
            DashMediaSource.Factory(mediaDataSourceFactory)
                .setDrmSessionManager(drmSessionManager)
                .createMediaSource(MediaItem.fromUri(STREAM_URL))

        val mediaSourceFactory: MediaSourceFactory =
            DefaultMediaSourceFactory(mediaDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        exoPlayer.addMediaSource(mediaSource)
        exoPlayer.addListener(PlayerEventListener())

        exoPlayer.playWhenReady = true
        binding.playerView.player = exoPlayer
        binding.playerView.requestFocus()
    }


    private class PlayerEventListener: Player.Listener {

        override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    println("IDLE")
                }
                Player.STATE_BUFFERING -> {
                    println("BUFFERING")
                }
                Player.STATE_READY -> {
                    println("READY")
                }
                Player.STATE_ENDED -> {
                    println("ENDED")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PLAYBACK_ERROR", error.message.toString())
        }
    }

    private fun releasePlayer() {
        exoPlayer.release()
    }

    public override fun onStart() {
        super.onStart()

        if (Util.SDK_INT > 23) initializePlayer()
    }

    public override fun onResume() {
        super.onResume()

        if (Util.SDK_INT <= 23) initializePlayer()
    }

    public override fun onPause() {
        super.onPause()

        if (Util.SDK_INT <= 23) releasePlayer()
    }

    public override fun onStop() {
        super.onStop()

        if (Util.SDK_INT > 23) releasePlayer()
    }

    companion object {
        const val STREAM_URL = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"
        const val LICENCE_URL = "https://proxy.uat.widevine.com/proxy?provider=widevine_test"
    }
}

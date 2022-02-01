package com.dedsec.exoplayertest

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.dedsec.exoplayertest.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.TrackNameProvider
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util


class PlayerActivity : Activity(), Player.Listener {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var binding : ActivityPlayerBinding
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var trackSelectorParameters: ParametersBuilder
    private val TAG = "TRACK_DETAILS"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.detailBtn.setOnClickListener { getTrackDetails() }
    }


    private fun initializePlayer() {

        val drmCallback = HttpMediaDrmCallback(LICENCE_URL, DefaultHttpDataSource.Factory())

        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setMultiSession(false)
            .build(drmCallback)

        val mediaDataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)

        val mediaSource = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
            .createMediaSource(MediaItem.fromUri(STREAM_URL))

//        val mediaSource =
//            DashMediaSource.Factory(mediaDataSourceFactory)
//                .setDrmSessionManager(drmSessionManager)
//                .createMediaSource(MediaItem.fromUri(STREAM_URL))

        val mediaSourceFactory: MediaSourceFactory =
            DefaultMediaSourceFactory(mediaDataSourceFactory)

        val handler = Handler()
        trackSelector = DefaultTrackSelector(this)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        bandwidthMeter.addEventListener(handler) { elapsedMs, bytesTransferred, _ ->
            //Log.d(TAG, "Bitrate (Mbps) = " + ((bytesTransferred * 8).toDouble() / (elapsedMs / 1000)) / 1000)
            binding.textView.text = (((bytesTransferred * 8).toDouble() / (elapsedMs / 1000)) / 1000).toString()
        }

        //bandwidthMeter.bitrateEstimate

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build()

        exoPlayer.addMediaSource(mediaSource)
        exoPlayer.addListener(this)



        exoPlayer.playWhenReady = true
        binding.playerView.player = exoPlayer
        binding.playerView.requestFocus()
    }


    private fun getTrackDetails() {

        val rendererInd = 0
        val mappedTrackInfo = Assertions.checkNotNull(trackSelector.currentMappedTrackInfo)

        val format: Format? = exoPlayer.videoFormat
        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(resources)
        if (format != null) {
            Log.e("VIDEO_BITRATE", trackNameProvider.getTrackName(format))
            Log.e("TRACK_INDEX", format.id.toString())
        }

        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {

            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)

            Log.d(TAG, "TRACK ITEM $rendererIndex")
            Log.d(TAG, "TRACK_TYPE: " + trackTypeToName(trackType))
            Log.d(TAG, "TRACK_TYPE_THIS: $trackNameProvider")

            for (groupIndex in 0 until trackGroupArray.length) {

                for (trackIndex in 0 until trackGroupArray[groupIndex].length) {

                    val trackName = DefaultTrackNameProvider(resources).getTrackName(
                        trackGroupArray[groupIndex].getFormat(trackIndex))

                    Log.d(TAG, "ITEM CODEC: $trackName")
                }
            }
        }

        val trackSelector = TrackSelectionDialogBuilder(
            this,
            "Select Track",
            trackSelector,
            rendererInd
        ).build()
        trackSelector.show()
    }

    private fun trackTypeToName(trackType: Int): String {
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> "TRACK_TYPE_VIDEO"
            C.TRACK_TYPE_AUDIO -> "TRACK_TYPE_AUDIO"
            C.TRACK_TYPE_TEXT -> "TRACK_TYPE_TEXT"
            else -> "Invalid track type"
        }
    }


    override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {
                println("IDLE")
            }
            Player.STATE_BUFFERING -> {
                println("BUFFERING")
                binding.progressBar.isVisible = true
            }
            Player.STATE_READY -> {
                println("READY")
                binding.progressBar.isGone = true
            }
            Player.STATE_ENDED -> {
                println("ENDED")
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e("PLAYBACK_ERROR_MESSAGE", error.message.toString())
        Log.e("PLAYBACK_ERROR_CAUSE", error.cause.toString())
        Log.e("PLAYBACK_ERROR_CNAME", error.errorCodeName)
        Log.e("PLAYBACK_ERROR_CODE", error.errorCode.toString())
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
        const val STREAM_URL = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
        const val LICENCE_URL = "https://proxy.uat.widevine.com/proxy?provider=widevine_test"
    }
}

package com.dedsec.exoplayertest

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.dedsec.exoplayertest.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.trackselection.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.TrackNameProvider
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import java.util.*


class PlayerActivity : Activity(), Player.Listener, AnalyticsListener {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var binding : ActivityPlayerBinding
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var trackSelectorParameters: ParametersBuilder
    private lateinit var trackGroupArray: TrackGroupArray
    private val TAG = "TRACK_DETAILS"

    private val trackList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.detailBtn.setOnClickListener { showDialog() }
    }


    private fun initializePlayer() {

        val drmCallback = HttpMediaDrmCallback(LICENCE_URL, DefaultHttpDataSource.Factory())

        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setMultiSession(false)
            .build(drmCallback)

        val mediaDataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)

//        val mediaSource = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
//            .createMediaSource(MediaItem.fromUri(STREAM_URL_M3U8))

        val mediaSource =
            DashMediaSource.Factory(mediaDataSourceFactory)
                //.setDrmSessionManager(drmSessionManager)
                .createMediaSource(MediaItem.fromUri(STREAM_CLEAR_DASH))

        val mediaSourceFactory: MediaSourceFactory =
            DefaultMediaSourceFactory(mediaDataSourceFactory)

        val handler = Handler()
        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(this, adaptiveTrackSelection)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        bandwidthMeter.addEventListener(handler) { elapsedMs, bytesTransferred, _ ->
            //Log.d(TAG, "Bitrate (Mbps) = " + ((bytesTransferred * 8).toDouble() / (elapsedMs / 1000)) / 1000)
            binding.textView.text = (((bytesTransferred * 8).toDouble() / (elapsedMs / 1000)) / 1000).toString()
        }

        //bandwidthMeter.bitrateEstimate
        //val params = trackSelectorParameters.setAllowVideoNonSeamlessAdaptiveness(true).build()
        //trackSelector.parameters = params

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build()

        exoPlayer.addMediaSource(mediaSource)
        exoPlayer.addListener(this)

        val mEventLogger = EventLogger(trackSelector)
        exoPlayer.addAnalyticsListener(this)

        exoPlayer.playWhenReady = true
        binding.playerView.player = exoPlayer
        binding.playerView.requestFocus()
    }


    private fun getTrackDetails() {
        trackList.clear()
        val rendererInd = 0
        val mappedTrackInfo = Assertions.checkNotNull(trackSelector.currentMappedTrackInfo)

        val format: Format? = exoPlayer.videoFormat
        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(resources)
        if (format != null) {
//            Log.e("VIDEO_BITRATE", trackNameProvider.getTrackName(format))
//            Log.e("TRACK_INDEX", format.id.toString())
        }

        for (rendererIndex in 0 until 1) {

            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)

            Log.d(TAG, "TRACK ITEM $rendererIndex")
            Log.d(TAG, "TRACK_TYPE: " + trackTypeToName(trackType))
            Log.d(TAG, "TRACK_TYPE_THIS: $trackNameProvider")
            Log.d(TAG, "GROUP_ARRAY: " + Gson().toJson(trackGroupArray))

            for (groupIndex in 0 until trackGroupArray.length) {

                for (trackIndex in 0 until trackGroupArray[groupIndex].length) {

                    val trackName = DefaultTrackNameProvider(resources).getTrackName(
                        trackGroupArray[groupIndex].getFormat(trackIndex))

                    Log.d(TAG, "ITEM CODEC: $trackName")
                    trackList.add(trackName)
                }
            }
        }
    }

    private fun showDialog() {
        val array: Array<String> = trackList.toArray(arrayOfNulls<String>(trackList.size))
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Track")
        builder.setItems(array) { _, index ->
            Log.e("TRACK_INDEX", index.toString())
            setTrack(index)
        }
        val dialog = builder.create()
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun setTrack(track: Int) {
        println("setVideoTrack: $track")
        val mappedTrackInfo = Assertions.checkNotNull(trackSelector.currentMappedTrackInfo)
        val parameters = trackSelector.parameters
        val builder = parameters.buildUpon()

        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {

            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            if (trackType == C.TRACK_TYPE_VIDEO) {
                builder.clearSelectionOverrides(rendererIndex)
                    .setRendererDisabled(rendererIndex, false)

                val tracks = intArrayOf(0)
                val override = SelectionOverride(track, *tracks)
                builder.setSelectionOverride(
                    rendererIndex,
                    mappedTrackInfo.getTrackGroups(rendererIndex),
                    override
                )
            }
        }
        trackSelector.setParameters(builder)
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
                getTrackDetails()
            }
            Player.STATE_ENDED -> {
                println("ENDED")
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e("PLAYBACK_ERROR_MESSAGE", error.toString())
        Log.e("PLAYBACK_ERROR_CAUSE", error.cause.toString())
        Log.e("PLAYBACK_ERROR_CNAME", error.errorCodeName)
        Log.e("PLAYBACK_ERROR_CODE", error.errorCode.toString())
        Log.e("ERROR_STACKTRACE", error.stackTraceToString())
        Log.e("ERROR_SUPP_EXCEPTIONS", error.suppressedExceptions.toString())
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?) {
        super.onVideoInputFormatChanged(eventTime, format, decoderReuseEvaluation)

        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(resources)
        Log.e("VIDEO_BITRATE", trackNameProvider.getTrackName(format))
        Log.e("TRACK_INDEX", format.id.toString())
        println(Gson().toJson(eventTime))
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
        const val STREAM_URL_M3U8 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
        const val STREAM_URL_MPD = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"
        const val STREAM_CLEAR_DASH = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd"
        const val LICENCE_URL = "https://proxy.uat.widevine.com/proxy?video_id=GTS_HW_SECURE_ALL&provider=widevine_test"
    }
}














//    fun setSelectedTrack(type: TrackType, groupIndex: Int, trackIndex: Int) {
//
//        val mappedTrackInfo = trackSelector.currentMappedTrackInfo
//        val tracksInfo: ExoPlayerRendererTracksInfo =
//            getExoPlayerTracksInfo(type, groupIndex, mappedTrackInfo)
//
//        val trackGroupArray =
//            if (tracksInfo.rendererTrackIndex == C.INDEX_UNSET || mappedTrackInfo == null) null
//            else mappedTrackInfo.getTrackGroups(tracksInfo.rendererTrackIndex)
//
//        if (trackGroupArray == null || trackGroupArray.length == 0 || trackGroupArray.length <= tracksInfo.rendererTrackGroupIndex) {
//            return
//        }
//
//        val group = trackGroupArray[tracksInfo.rendererTrackGroupIndex]
//        if (group.length <= trackIndex) {
//            return
//        }
//
//        val parametersBuilder = trackSelector.buildUponParameters()
//        for (rendererTrackIndex in tracksInfo.rendererTrackIndexes) {
//            parametersBuilder.clearSelectionOverrides(rendererTrackIndex)
//            if (tracksInfo.rendererTrackIndex === rendererTrackIndex) {
//                parametersBuilder.setSelectionOverride(rendererTrackIndex, trackGroupArray,
//                    SelectionOverride(tracksInfo.rendererTrackGroupIndex, trackIndex))
//                parametersBuilder.setRendererDisabled(rendererTrackIndex, false)
//            } else parametersBuilder.setRendererDisabled(rendererTrackIndex, true)
//        }
//        trackSelector.setParameters(parametersBuilder)
//    }



//        val defaultTrackSelector = DefaultTrackSelector(this).apply {
//            TrackSelectionParameters.Builder(this@PlayerActivity)
//                .setTrackSelectionOverrides()
//                .build()
//        }

//        val overrides = TrackSelectionOverrides.Builder()
//            .setOverrideForType(TrackSelectionOverride(TrackGroup(format!!)))
//            .build()
//
//        Log.e("OVERRIDES", overrides.asList().toString())
//
//        exoPlayer.trackSelectionParameters.buildUpon()
//            .setTrackSelectionOverrides(overrides)
//            .build()

//        val trackSelector = TrackSelectionDialogBuilder(
//            this,
//            "Select Track",
//            trackSelector,
//            rendererInd
//        ).build()
//        trackSelector.show()
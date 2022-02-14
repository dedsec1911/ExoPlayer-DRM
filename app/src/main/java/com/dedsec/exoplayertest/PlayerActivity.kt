package com.dedsec.exoplayertest

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity.CENTER
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.dedsec.exoplayertest.databinding.ActivityPlayerBinding
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdsLoader.AdsLoadedListener
import com.google.ads.interactivemedia.v3.api.AdsManager
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.TrackNameProvider
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize
import com.google.common.collect.ImmutableList
import com.google.gson.Gson
import com.michael.easydialog.EasyDialog
import kotlin.math.roundToInt


class PlayerActivity : Activity(), Player.Listener, AnalyticsListener, AdEvent.AdEventListener,
    AdErrorEvent.AdErrorListener, AdsLoadedListener  {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var binding : ActivityPlayerBinding
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var trackSelectorParameters: DefaultTrackSelector.ParametersBuilder
    private lateinit var trackGroupArray: TrackGroupArray

    private var adsLoader: ImaAdsLoader? = null
    private var adPlaybackState: AdPlaybackState? = null
    private var adsManager: AdsManager? = null
    private var videoProgressUpdate: VideoProgressUpdate? = null

    private val formatList: ArrayList<String> = ArrayList()
    private val bitrateList: ArrayList<String> = ArrayList()
    private var qualityDialog: EasyDialog? = null

    private val TAG = "TRACK_DETAILS"

    private val trackList = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adsLoader = ImaAdsLoader.Builder(this)
            .setAdEventListener(this)
            .setAdErrorListener(this)
            .build()

        binding.detailBtn.setOnClickListener { showDialog() }

    }


    private fun initializePlayer() {

//        val drmCallback = HttpMediaDrmCallback(LICENCE_URL, DefaultHttpDataSource.Factory())
//
//        val drmSessionManager = DefaultDrmSessionManager.Builder()
//            .setMultiSession(false)
//            .build(drmCallback)

        val mediaDataSourceFactory: DataSource.Factory =
            DefaultDataSource.Factory(this)

        val drmConfig: MediaItem.DrmConfiguration =
            MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(LICENCE_URL)
                .build()

        val subtitle: MediaItem.SubtitleConfiguration =
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(SUBS))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
                .build()

        val ads: MediaItem.AdsConfiguration =
            MediaItem.AdsConfiguration.Builder(Uri.parse(VMAP_PODS))
                .build()

        val mediaItem: MediaItem = MediaItem.Builder()
            .setUri(Uri.parse(STREAM_URL_MPD))
            .setDrmConfiguration(drmConfig)
            //.setAdsConfiguration(ads)
            .setSubtitleConfigurations(ImmutableList.of(subtitle))
            .build()

//        val mediaSource =
//            HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
//                .createMediaSource(MediaItem.fromUri(STREAM_M3U8_SUB))

        val mediaSource =
            DashMediaSource.Factory(mediaDataSourceFactory)
                //.setDrmSessionManager(drmSessionManager)
                .createMediaSource(mediaItem)

        val mediaSourceFactory: MediaSourceFactory =
            DefaultMediaSourceFactory(mediaDataSourceFactory)
                .setAdsLoaderProvider { adsLoader }
                .setAdViewProvider(binding.playerView)

        val handler = Handler()
        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(this, adaptiveTrackSelection)
        trackSelector.buildUponParameters().setRendererDisabled(C.TRACK_TYPE_TEXT, true).build()
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

        //exoPlayer.addMediaSource(mediaSource)
        exoPlayer.addMediaItem(mediaItem)
        exoPlayer.addListener(this)

        val mEventLogger = EventLogger(trackSelector)
        exoPlayer.addAnalyticsListener(mEventLogger)

        exoPlayer.playWhenReady = true
        binding.playerView.player = exoPlayer
        adsLoader?.setPlayer(exoPlayer)
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

        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {

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
        val trackSelector = TrackSelectionDialogBuilder(
            this,
            "Select Track",
            trackSelector,
            0
        ).build()
        trackSelector.show()
    }

    private fun trackListDetails() {
        formatList.clear()
        bitrateList.clear()

        if (trackSelector.currentMappedTrackInfo != null) {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo
            for (rendererIndex in 0 until mappedTrackInfo!!.rendererCount) {

                val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)

                for (groupIndex in 0 until trackGroupArray.length) {

                    for (trackIndex in 0 until trackGroupArray[groupIndex].length) {
                        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(resources)
                        val trackName = trackNameProvider.getTrackName(
                            trackGroupArray[groupIndex].getFormat(trackIndex))
                        val widthHeight = trackName.split(",").toTypedArray()
                        val height = widthHeight[0].split("×").toTypedArray()

                        if (rendererIndex == 0) {
                            formatList.add(height[1] + "p")
                            bitrateList.add(widthHeight[1].replace("Mbps","").trim())
                        }
                    }
                }
            }
        }
    }

    private fun populatePopupmenus() {
        val v: View = this.layoutInflater.inflate(R.layout.custom_track_selection_dialog, null)
        val lLayout: LinearLayout = v.findViewById(R.id.popup_element)

        for (i in formatList.indices) {
            val tv = TextView(this)
            tv.text = formatList[i]
            tv.id = i
            tv.setTextColor(Color.WHITE)
            tv.gravity = CENTER
            tv.setPadding(0, 5, 0, 5)
            tv.textSize = 18f
            tv.setOnClickListener {

                val id = tv.id
                if (id == 0) {
                    changeQuality(bitrateList[id].toDouble() * 1000000)
                }
                if (id == 1) {
                    changeQuality(bitrateList[id].toDouble() * 1000000)
                }
                if (id == 2) {
                    changeQuality(bitrateList[id].toDouble() * 1000000)
                }
                if (id == 3) {
                    changeQuality(bitrateList[id].toDouble() * 1000000)
                }
                if (id == 4) {
                    changeQuality(bitrateList[id].toDouble() * 1000000)
                }
            }
            if (tv.parent != null) {
                (tv.parent as ViewGroup).removeView(tv)
            }
            lLayout.addView(tv)
        }

        qualityDialog =
            EasyDialog(this@PlayerActivity)
                .setLayout(v)
                .setLocationByAttachedView(v)
                .setGravity(EasyDialog.GRAVITY_BOTTOM)
                .setTouchOutsideDismiss(true)
                .setMatchParent(false)
                .show()
    }


    private fun changeQuality(bitrate: Double) {
        Log.e("RECEIVED_BITRATE", bitrate.toString())
        val trackSelectorParameters = DefaultTrackSelector.ParametersBuilder(this)
            .setMaxVideoBitrate(bitrate.roundToInt())
            .build()

        trackSelector.parameters = trackSelectorParameters
        qualityDialog?.dismiss()
    }


    private fun trackTypeToName(trackType: Int): String {
        return when (trackType) {
            C.TRACK_TYPE_VIDEO -> "TRACK_TYPE_VIDEO"
            C.TRACK_TYPE_AUDIO -> "TRACK_TYPE_AUDIO"
            C.TRACK_TYPE_TEXT -> "TRACK_TYPE_TEXT"
            else -> "Invalid track type"
        }
    }

    override fun onAdEvent(p0: AdEvent?) {
        //Log.e("AD_EVENT", p0.toString())
        //Log.e("AD_EVENT_ad", p0?.ad?.title.toString())
        //adsManager.adCuePoints
        Log.e("AD_POD_POS", p0?.ad?.adPodInfo?.adPosition.toString())
        Log.e("AD_POD_TOTAL", p0?.ad?.adPodInfo?.totalAds.toString())
        Log.e("AD_POD_INDEX", p0?.ad?.adPodInfo?.podIndex.toString())

    }

    override fun onAdError(p0: AdErrorEvent?) {
        Log.e("AD_ERROR", Gson().toJson(p0))
    }

    override fun onAdsManagerLoaded(p0: AdsManagerLoadedEvent) {
        adsManager = p0.adsManager
        Log.e("CUE_POINTS", adsManager?.adCuePoints.toString())
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
                //getTrackDetails()
                trackListDetails()
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

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (timeline.isEmpty) {
            return
        }
        val period = timeline.getPeriod(exoPlayer.currentPeriodIndex, Timeline.Period())
        val adGroupTimesUs = LongArray(period.adGroupCount)
        for (i in adGroupTimesUs.indices) {
            adGroupTimesUs[i] = period.getAdGroupTimeUs(i)
        }
        Log.e("AD_GROUPS", Gson().toJson(adGroupTimesUs))
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

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        Log.e("Video Height: ", videoSize.height.toString())
        Log.e("Video Width: ", videoSize.width.toString())
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

    override fun onDestroy() {
        super.onDestroy()
        adsLoader?.release()
        Log.e("ADS_DESTROYED", "X")
    }

    companion object {
        const val STREAM_URL_M3U8 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"
        const val STREAM_M3U8_SUB = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
        const val STREAM_URL_MPD = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"
        const val STREAM_WEBVTT_MPD = "https://dash.akamaized.net/dash264/TestCasesIOP41/CMAF/UnifiedStreaming/ToS_AVC_MultiRate_MultiRes_AAC_Eng_WebVTT.mpd"
        const val STREAM_CLEAR_DASH = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd"
        const val LICENCE_URL = "https://proxy.uat.widevine.com/proxy?provider=widevine_test"
        const val SUBS = "https://bitdash-a.akamaihd.net/content/sintel/hls/subtitles_en.vtt"

        const val ADS_URL = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator="
        const val VMAP_PODS = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=300x250&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpostpod&cmsid=496&vid=short_onecue&correlator="
        const val STOCK_AD = "https://storage.googleapis.com/gvabox/media/samples/stock.mp4"
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



//private fun showCustomDialog() {
//    val array: Array<String> = trackList.toArray(arrayOfNulls<String>(trackList.size))
//    val builder = AlertDialog.Builder(this)
//    builder.setTitle("Select Track")
//    builder.setItems(array) { _, index ->
//        Log.e("TRACK_INDEX", index.toString())
//        setTrack(index)
//    }
//    val dialog = builder.create()
//    dialog.show()
//}
//
//@Suppress("DEPRECATION")
//private fun setTrack(track: Int) {
//    println("setVideoTrack: $track")
//    val mappedTrackInfo = Assertions.checkNotNull(trackSelector.currentMappedTrackInfo)
//    val parameters = trackSelector.parameters
//    val builder = parameters.buildUpon()
//
//    for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
//
//        val trackType = mappedTrackInfo.getRendererType(rendererIndex)
//        if (trackType == C.TRACK_TYPE_VIDEO) {
//            builder.clearSelectionOverrides(rendererIndex)
//                .setRendererDisabled(rendererIndex, false)
//
//            val tracks = intArrayOf(0)
//            val override = SelectionOverride(track, *tracks)
//            builder.setSelectionOverride(
//                rendererIndex,
//                mappedTrackInfo.getTrackGroups(rendererIndex),
//                override
//            )
//        }
//    }
//    trackSelector.setParameters(builder)
//}
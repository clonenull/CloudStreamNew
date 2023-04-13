package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.download.DOWNLOAD_ACTION_DOWNLOAD
import com.lagradost.cloudstream3.ui.download.DownloadButtonViewHolder
import com.lagradost.cloudstream3.ui.download.DownloadClickEvent
import com.lagradost.cloudstream3.ui.download.EasyDownloadButton
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.result_episode.view.*
import kotlinx.android.synthetic.main.result_episode.view.episode_text
import kotlinx.android.synthetic.main.result_episode_large.view.*
import kotlinx.android.synthetic.main.result_episode_large.view.episode_filler
import kotlinx.android.synthetic.main.result_episode_large.view.episode_progress
import kotlinx.android.synthetic.main.result_episode_large.view.result_episode_download
import kotlinx.android.synthetic.main.result_episode_large.view.result_episode_progress_downloaded
import java.util.*

const val ACTION_PLAY_EPISODE_IN_PLAYER = 1
const val ACTION_PLAY_EPISODE_IN_VLC_PLAYER = 2
const val ACTION_PLAY_EPISODE_IN_BROWSER = 3

const val ACTION_CHROME_CAST_EPISODE = 4
const val ACTION_CHROME_CAST_MIRROR = 5

const val ACTION_DOWNLOAD_EPISODE = 6
const val ACTION_DOWNLOAD_MIRROR = 7

const val ACTION_RELOAD_EPISODE = 8
const val ACTION_COPY_LINK = 9

const val ACTION_SHOW_OPTIONS = 10

const val ACTION_CLICK_DEFAULT = 11
const val ACTION_SHOW_TOAST = 12
const val ACTION_SHOW_DESCRIPTION = 15

const val ACTION_DOWNLOAD_EPISODE_SUBTITLE = 13
const val ACTION_DOWNLOAD_EPISODE_SUBTITLE_MIRROR = 14

data class EpisodeClickEvent(val action: Int, val data: ResultEpisode)

class EpisodeAdapter(
    var cardList: List<ResultEpisode>,
    private val hasDownloadSupport: Boolean,
    private val clickCallback: (EpisodeClickEvent) -> Unit,
    private val downloadClickCallback: (DownloadClickEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val mBoundViewHolders: HashSet<DownloadButtonViewHolder> = HashSet()
    private fun getAllBoundViewHolders(): Set<DownloadButtonViewHolder?>? {
        return Collections.unmodifiableSet(mBoundViewHolders)
    }

    fun killAdapter() {
        getAllBoundViewHolders()?.forEach { view ->
            view?.downloadButton?.dispose()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is DownloadButtonViewHolder) {
            holder.downloadButton.dispose()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is DownloadButtonViewHolder) {
            holder.downloadButton.dispose()
            mBoundViewHolders.remove(holder)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder is DownloadButtonViewHolder) {
            holder.reattachDownloadButton()
        }
    }

    @LayoutRes
    private var layout: Int = 0
    fun updateLayout() {
        // layout =
        //     if (cardList.filter { it.poster != null }.size >= cardList.size / 2f) // If over half has posters then use the large layout
        //          R.layout.result_episode_large
        //      else R.layout.result_episode
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        /*val layout = if (cardList.filter { it.poster != null }.size >= cardList.size / 2)
            R.layout.result_episode_large
        else R.layout.result_episode*/

        return EpisodeCardViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.result_episode_both, parent, false),
            hasDownloadSupport,
            clickCallback,
            downloadClickCallback
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is EpisodeCardViewHolder -> {
                holder.bind(cardList[position])
                mBoundViewHolders.add(holder)
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class EpisodeCardViewHolder
    constructor(
        itemView: View,
        private val hasDownloadSupport: Boolean,
        private val clickCallback: (EpisodeClickEvent) -> Unit,
        private val downloadClickCallback: (DownloadClickEvent) -> Unit,
    ) : RecyclerView.ViewHolder(itemView), DownloadButtonViewHolder {
        override var downloadButton = EasyDownloadButton()


        var episodeDownloadBar: ContentLoadingProgressBar? = null
        var episodeDownloadImage: ImageView? = null
        var localCard: ResultEpisode? = null

        @SuppressLint("SetTextI18n")
        fun bind(card: ResultEpisode) {
            localCard = card

            val (parentView, otherView) = if (card.poster == null) {
                itemView.episode_holder to itemView.episode_holder_large
            } else {
                itemView.episode_holder_large to itemView.episode_holder
            }
            parentView.isVisible = true
            otherView.isVisible = false

            val episodeText: TextView = parentView.episode_text
            val episodeFiller: MaterialButton? = parentView.episode_filler
            val episodeRating: TextView? = parentView.episode_rating
            val episodeDescript: TextView? = parentView.episode_descript
            val episodeProgress: ContentLoadingProgressBar? = parentView.episode_progress
            val episodePoster: ImageView? = parentView.episode_poster

            episodeDownloadBar =
                parentView.result_episode_progress_downloaded
            episodeDownloadImage = parentView.result_episode_download

            val name =
                if (card.name == null) "${episodeText.context.getString(R.string.episode)} ${card.episode}" else "${card.episode}. ${card.name}"
            episodeFiller?.isVisible = card.isFiller == true
            episodeText.text =
                name//if(card.isFiller == true) episodeText.context.getString(R.string.filler).format(name) else name
            episodeText.isSelected = true // is needed for text repeating

            val displayPos = card.getDisplayPosition()
            episodeProgress?.max = (card.duration / 1000).toInt()
            episodeProgress?.progress = (displayPos / 1000).toInt()
            episodeProgress?.isVisible = displayPos > 0L

            episodePoster?.isVisible = episodePoster?.setImage(card.poster) == true

            if (card.rating != null) {
                episodeRating?.text = episodeRating?.context?.getString(R.string.rated_format)
                    ?.format(card.rating.toFloat() / 10f)
            } else {
                episodeRating?.text = ""
            }

            episodeRating?.isGone = episodeRating?.text.isNullOrBlank()

            episodeDescript?.apply {
                text = card.description.html()
                isGone = text.isNullOrBlank()
                setOnClickListener {
                    clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_DESCRIPTION, card))
                }
            }

            episodePoster?.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
            }

            episodePoster?.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_TOAST, card))
                return@setOnLongClickListener true
            }

            parentView.setOnClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_CLICK_DEFAULT, card))
            }

            if (parentView.context.isTrueTvSettings()) {
                parentView.isFocusable = true
                parentView.isFocusableInTouchMode = true
                parentView.touchscreenBlocksFocus = false
            }

            parentView.setOnLongClickListener {
                clickCallback.invoke(EpisodeClickEvent(ACTION_SHOW_OPTIONS, card))

                return@setOnLongClickListener true
            }

            episodeDownloadImage?.isVisible = hasDownloadSupport
            episodeDownloadBar?.isVisible = hasDownloadSupport
            reattachDownloadButton()
        }

        override fun reattachDownloadButton() {
            downloadButton.dispose()
            val card = localCard
            if (hasDownloadSupport && card != null) {
                val downloadInfo = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(
                    itemView.context,
                    card.id
                )

                downloadButton.setUpButton(
                    downloadInfo?.fileLength,
                    downloadInfo?.totalBytes,
                    episodeDownloadBar ?: return,
                    episodeDownloadImage ?: return,
                    null,
                    VideoDownloadHelper.DownloadEpisodeCached(
                        card.name,
                        card.poster,
                        card.episode,
                        card.season,
                        card.id,
                        card.parentId,
                        card.rating,
                        card.description,
                        System.currentTimeMillis(),
                    )
                ) {
                    if (it.action == DOWNLOAD_ACTION_DOWNLOAD) {
                        clickCallback.invoke(EpisodeClickEvent(ACTION_DOWNLOAD_EPISODE, card))
                    } else {
                        downloadClickCallback.invoke(it)
                    }
                }
            }
        }
    }
}

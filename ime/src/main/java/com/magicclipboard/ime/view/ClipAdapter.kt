package com.magicclipboard.ime.view

import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.magicclipboard.data.model.ClipContentKind
import com.magicclipboard.data.model.ClipEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipAdapter(
    private val imageLoader: suspend (Long) -> Bitmap?,
    private val onClipTapped: (ClipEntry) -> Unit,
) : RecyclerView.Adapter<ClipAdapter.ClipViewHolder>() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var items: List<ClipEntry> = emptyList()

    fun submitList(nextItems: List<ClipEntry>) {
        items = nextItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ClipViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val image = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(88, 88)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val text = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 24
            }
            maxLines = 2
            textSize = 15f
        }
        container.addView(image)
        container.addView(text)
        return ClipViewHolder(container, image, text)
    }

    override fun onBindViewHolder(
        holder: ClipViewHolder,
        position: Int,
    ) {
        holder.bind(items[position], imageLoader, onClipTapped, scope)
    }

    override fun getItemCount(): Int = items.size

    class ClipViewHolder(
        itemView: View,
        private val imageView: ImageView,
        private val textView: TextView,
    ) : RecyclerView.ViewHolder(itemView) {
        private var imageJob: Job? = null

        fun bind(
            clip: ClipEntry,
            imageLoader: suspend (Long) -> Bitmap?,
            onClipTapped: (ClipEntry) -> Unit,
            scope: CoroutineScope,
        ) {
            imageJob?.cancel()
            textView.text = clip.previewText
            imageView.setImageDrawable(null)
            if (clip.kind == ClipContentKind.IMAGE) {
                textView.text = clip.previewText
                imageView.setImageDrawable(
                    AppCompatResources.getDrawable(itemView.context, android.R.drawable.ic_menu_gallery),
                )
                imageJob = scope.launch {
                    val bitmap = withContext(Dispatchers.IO) { imageLoader(clip.id) }
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } else {
                imageView.setImageDrawable(
                    AppCompatResources.getDrawable(itemView.context, android.R.drawable.ic_menu_edit),
                )
            }
            itemView.setOnClickListener { onClipTapped(clip) }
        }
    }
}

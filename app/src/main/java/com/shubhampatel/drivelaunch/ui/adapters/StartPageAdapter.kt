package com.shubhampatel.drivelaunch.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shubhampatel.drivelaunch.R
import com.shubhampatel.drivelaunch.bookmarks.BookmarkManager
import com.shubhampatel.drivelaunch.databinding.ItemStartPageSlotBinding

data class SlotItem(
    val url: String,
    val hasCachedIcon: Boolean
)

class StartPageAdapter(
    private val bookmarkManager: BookmarkManager,
    private val onSlotClick: (String) -> Unit,
    private val onReordered: (List<SlotItem>) -> Unit,
    private val resolveThemeColor: (Int) -> Int
) : ListAdapter<SlotItem, StartPageAdapter.SlotViewHolder>(SlotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val binding = ItemStartPageSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(fromPosition)
        list.add(toPosition, item)
        submitList(list)
        onReordered(list)
    }

    inner class SlotViewHolder(private val binding: ItemStartPageSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SlotItem) {
            val url = item.url
            val context = itemView.context
            val isEmpty = url.isEmpty()

            binding.iconContainer.removeAllViews()
            binding.iconContainer.addView(
                bookmarkManager.createSiteIconBadge(
                    url = if (isEmpty) null else url,
                    sizeDp = 64f,
                    cornerRadiusDp = 18f,
                    paddingDp = 10f,
                    backgroundColor = resolveThemeColor(
                        if (isEmpty) com.google.android.material.R.attr.colorSecondaryContainer
                        else com.google.android.material.R.attr.colorPrimaryContainer
                    ),
                    showAddOnEmptyUrl = true
                )
            )

            binding.labelView.text = if (isEmpty) {
                context.getString(R.string.start_page_slot_empty_title)
            } else {
                bookmarkManager.displayTitleForUrl(url)
            }
            binding.labelView.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))

            binding.root.setOnClickListener { onSlotClick(url) }
            binding.root.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.90f).scaleY(0.90f).setDuration(120)
                            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
                    }
                }
                false
            }
        }
    }

    class SlotDiffCallback : DiffUtil.ItemCallback<SlotItem>() {
        override fun areItemsTheSame(oldItem: SlotItem, newItem: SlotItem): Boolean = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: SlotItem, newItem: SlotItem): Boolean = oldItem == newItem
    }
}

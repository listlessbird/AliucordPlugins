package com.github.listlessbird.plugins

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.api.SettingsAPI
import com.aliucord.utils.DimenUtils
import com.aliucord.views.TextInput
import com.aliucord.widgets.BottomSheet
import com.discord.stores.StoreStream
import com.lytefast.flexinput.R

@SuppressLint("SetTextI18n")
@Suppress("MISSING_DEPENDENCY_SUPERCLASS")
class TapTapReactSettings(private val settings: SettingsAPI): BottomSheet() {

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        val ctx = view.context

        val header = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply {
            text = "Tap Tap React"
        }
        addView(header)

//        joy
        val currentEmoji = settings.getString("emoji", "\uD83D\uDE02")
        val currentEmojiName = findEmojiName(currentEmoji) ?: "unknown"

        val currentLabel = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
            text = "Current: $currentEmoji ($currentEmojiName)"
        }
        addView(currentLabel)

        val pickerButton = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
            text = "Choose Emoji"
            setOnClickListener {
                showEmojiPicker { emojiName, emoji ->
                    settings.setString("emoji", emoji)
                    currentLabel.text = "Current: $emoji ($emojiName)"
                }
            }
        }
        addView(pickerButton)
    }

    private fun findEmojiName(emoji: String): String? {
        val emojiMap = StoreStream.getEmojis().unicodeEmojisNamesMap
        return emojiMap.entries.find { it.value.uniqueId == emoji }?.key
    }

    private fun showEmojiPicker(onEmojiSelected: (name: String, emoji: String) -> Unit) {
        try {
            val picker = EmojiPickerSheet()
            picker.onEmojiSelected = onEmojiSelected
            picker.show(parentFragmentManager, "EmojiPicker")
        } catch (e: Throwable) {
            com.aliucord.Logger("TapTapReact").error("Failed to show emoji picker", e)
        }
    }

    @Suppress("MISSING_DEPENDENCY_SUPERCLASS")
    class EmojiPickerSheet : BottomSheet() {

        var onEmojiSelected: ((name: String, emoji: String) -> Unit)? = null

        private data class EmojiItem(val name: String, val emoji: String)

        private inner class EmojiAdapter(
            private val allItems: List<EmojiItem>
        ) : RecyclerView.Adapter<EmojiAdapter.ViewHolder>(), Filterable {

            private var filteredItems = allItems

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                return ViewHolder(TextView(parent.context, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                holder.bind(filteredItems[position])
            }

            override fun getItemCount(): Int = filteredItems.size

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val query = constraint?.toString()?.lowercase() ?: ""
                        val filtered = if (query.isEmpty()) {
                            allItems
                        } else {
                            allItems.filter { it.name.lowercase().contains(query) }
                        }
                        return FilterResults().apply { values = filtered }
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        filteredItems = results?.values as? List<EmojiItem> ?: allItems
                        notifyDataSetChanged()
                    }
                }
            }

            inner class ViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
                fun bind(item: EmojiItem) {
                    textView.run {
                        text = "${item.emoji} ${item.name}"
                        setOnClickListener {
                            onEmojiSelected?.invoke(item.name, item.emoji)
                            dismiss()
                        }
                    }
                }
            }
        }


        override fun onViewCreated(view: View, bundle: Bundle?) {
            try {
                super.onViewCreated(view, bundle)

                val ctx = view.context
                val p = DimenUtils.defaultPadding

                val header = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Header).apply {
                    text = "Select Emoji"
                }
                addView(header)

                val emojiMap = StoreStream.getEmojis().unicodeEmojisNamesMap

                val emojiList = emojiMap.entries
                    .sortedBy { it.key }
                    .map { (name, emoji) -> EmojiItem(name, emoji.uniqueId) }


                val adapter = EmojiAdapter(emojiList)

                TextInput(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(p, p / 2, p, p / 2)
                    }
                    root.hint = ctx.getString(R.h.search)
                    editText.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(editable: Editable?) {
                            adapter.filter.filter(editText.text.toString())
                        }
                    })
                }.also { addView(it) }

                RecyclerView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        DimenUtils.dpToPx(400)
                    )
                    this.adapter = adapter
                    layoutManager = LinearLayoutManager(ctx)
                }.also { addView(it) }

            } catch (e: Throwable) {
                com.aliucord.Logger("TapTapReact").error("Failed to show emoji picker", e)
                throw e
            }
        }
    }
}

package com.looker.droidify.screen

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.circularreveal.CircularRevealFrameLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.looker.droidify.R
import com.looker.droidify.content.Preferences
import com.looker.droidify.database.Database
import com.looker.droidify.entity.ProductItem
import com.looker.droidify.entity.Repository
import com.looker.droidify.network.CoilDownloader
import com.looker.droidify.utility.Utils
import com.looker.droidify.utility.extension.resources.*
import com.looker.droidify.utility.extension.text.nullIfEmpty
import com.looker.droidify.widget.CursorRecyclerAdapter

class ProductsAdapter(private val onClick: (ProductItem) -> Unit) :
    CursorRecyclerAdapter<ProductsAdapter.ViewType, RecyclerView.ViewHolder>() {
    private var lastPosition = 0

    enum class ViewType { PRODUCT, LOADING, EMPTY }

    private class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = itemView.findViewById<MaterialTextView>(R.id.name)!!
        val status = itemView.findViewById<MaterialTextView>(R.id.status)!!
        val summary = itemView.findViewById<MaterialTextView>(R.id.summary)!!
        val icon = itemView.findViewById<ShapeableImageView>(R.id.icon)!!

        val progressIcon: Drawable
        val defaultIcon: Drawable

        init {
            val (progressIcon, defaultIcon) = Utils.getDefaultApplicationIcons(icon.context)
            this.progressIcon = progressIcon
            this.defaultIcon = defaultIcon
        }
    }

    private class LoadingViewHolder(context: Context) :
        RecyclerView.ViewHolder(CircularRevealFrameLayout(context)) {
        init {
            itemView as CircularRevealFrameLayout
            val progressBar = CircularProgressIndicator(itemView.context)
            itemView.addView(progressBar)
            itemView.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
        }
    }

    private class EmptyViewHolder(context: Context) :
        RecyclerView.ViewHolder(MaterialTextView(context)) {
        val text: MaterialTextView
            get() = itemView as MaterialTextView

        init {
            itemView as MaterialTextView
            itemView.gravity = Gravity.CENTER
            itemView.resources.sizeScaled(20).let { itemView.setPadding(it, it, it, it) }
            itemView.typeface = TypefaceExtra.light
            itemView.setTextColor(context.getColorFromAttr(android.R.attr.colorPrimary))
            itemView.setTextSizeScaled(20)
            itemView.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
        }
    }

    var repositories: Map<Long, Repository> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var emptyText: String = ""
        set(value) {
            if (field != value) {
                field = value
                if (isEmpty) {
                    notifyDataSetChanged()
                }
            }
        }

    override val viewTypeClass: Class<ViewType>
        get() = ViewType::class.java

    private val isEmpty: Boolean
        get() = super.getItemCount() == 0

    override fun getItemCount(): Int = if (isEmpty) 1 else super.getItemCount()
    override fun getItemId(position: Int): Long = if (isEmpty) -1 else super.getItemId(position)

    override fun getItemEnumViewType(position: Int): ViewType {
        return when {
            !isEmpty -> ViewType.PRODUCT
            cursor == null -> ViewType.LOADING
            else -> ViewType.EMPTY
        }
    }

    private fun getProductItem(position: Int): ProductItem {
        return Database.ProductAdapter.transformItem(moveTo(position))
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: ViewType
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            ViewType.PRODUCT -> ProductViewHolder(parent.inflate(R.layout.product_item)).apply {
                itemView.setOnClickListener { onClick(getProductItem(adapterPosition)) }
            }
            ViewType.LOADING -> LoadingViewHolder(parent.context)
            ViewType.EMPTY -> EmptyViewHolder(parent.context)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (Preferences[Preferences.Key.ListAnimation]) {
            holder.itemView.clearAnimation()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemEnumViewType(position)) {
            ViewType.PRODUCT -> {
                holder as ProductViewHolder
                val productItem = getProductItem(position)
                holder.name.text = productItem.name
                holder.summary.text =
                    if (productItem.name == productItem.summary) "" else productItem.summary
                holder.summary.visibility =
                    if (holder.summary.text.isNotEmpty()) View.VISIBLE else View.GONE
                val repository: Repository? = repositories[productItem.repositoryId]
                if ((productItem.icon.isNotEmpty() || productItem.metadataIcon.isNotEmpty()) && repository != null) {
                    holder.icon.load(
                        CoilDownloader.createIconUri(
                            holder.icon, productItem.packageName,
                            productItem.icon, productItem.metadataIcon, repository
                        )
                    ) {
                        transformations(RoundedCornersTransformation(4.toPx))
                        placeholder(holder.progressIcon)
                        error(holder.defaultIcon)
                    }
                } else {
                    holder.icon.clear()
                    holder.icon.setImageDrawable(holder.defaultIcon)
                }
                holder.status.apply {
                    if (productItem.canUpdate) {
                        text = productItem.version
                        if (background == null) {
                            resources.sizeScaled(4).let { setPadding(it, 0, it, 0) }
                            setTextColor(holder.status.context.getColorFromAttr(android.R.attr.colorBackground))
                            background = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                null
                            ).apply {
                                color =
                                    holder.status.context.getColorFromAttr(android.R.attr.colorAccent)
                                cornerRadius = holder.status.resources.sizeScaled(2).toFloat()
                            }
                        }
                    } else {
                        text = productItem.installedVersion.nullIfEmpty() ?: productItem.version
                        if (background != null) {
                            setPadding(0, 0, 0, 0)
                            setTextColor(holder.status.context.getColorFromAttr(android.R.attr.colorPrimary))
                            background = null
                        }
                    }
                }
                val enabled = productItem.compatible || productItem.installedVersion.isNotEmpty()
                sequenceOf(holder.name, holder.status, holder.summary).forEach {
                    it.isEnabled = enabled
                }
            }
            ViewType.LOADING -> {
                // Do nothing
            }
            ViewType.EMPTY -> {
                holder as EmptyViewHolder
                holder.text.text = emptyText
            }
        }::class
        if (Preferences[Preferences.Key.ListAnimation]) {
            setAnimation(holder.itemView, holder.adapterPosition)
        }
    }

    private fun setAnimation(itemView: View, position: Int) {
        val animation = AnimationUtils.loadAnimation(
            itemView.context,
            if (position > lastPosition) R.anim.slide_up else R.anim.slide_down
        )
        itemView.startAnimation(animation)
        lastPosition = position
    }
}
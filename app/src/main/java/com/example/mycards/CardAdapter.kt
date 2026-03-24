package com.example.mycards

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.mycards.databinding.ItemCardBinding

class CardAdapter(
    private val onCardClick: (LoyaltyCard) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    private var cards: List<LoyaltyCard> = emptyList()
    private var labelMap: Map<Int, String> = emptyMap()

    fun updateList(newCards: List<LoyaltyCard>) {
        cards    = newCards
        labelMap = CardLabelUtils.computeDisplayLabels(newCards)
        notifyDataSetChanged()
    }

    inner class CardViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: LoyaltyCard) {
            binding.textCardName.text  = card.name
            binding.textCardLabel.text = labelMap[card.id] ?: CardLabelUtils.computeDisplayLabels(listOf(card))[card.id] ?: card.name
            binding.textCardLabel.setBackgroundColor(CardLabelUtils.labelBackgroundColor(card.name))
            binding.root.setOnClickListener { onCardClick(card) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position])
    }

    override fun getItemCount(): Int = cards.size
}

fun loadImage(imageView: ImageView, resId: Int, uri: String?) {
    when {
        uri != null -> imageView.setImageURI(Uri.parse(uri))
        resId != 0  -> imageView.setImageResource(resId)
    }
}

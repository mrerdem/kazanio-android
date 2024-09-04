package com.creadeep.kazanio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup

class GeneratorViewPagerAdapter (val gameType: Int, names: List<String>): RecyclerView.Adapter<GeneratorViewPagerAdapter.NumberSelectorViewHolder>() {

    private val tabNames = names
    private var chipNum = 9 // Number of chips in a single line

    override fun getItemCount(): Int {
        return tabNames.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NumberSelectorViewHolder {
        return when (gameType) {
            2, 8 -> { // Sayisal Loto
                chipNum = 9
                NumberSelectorViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.number_selector_sayisal_loto, parent, false))
            }
            3, 9 -> { // Super Loto
                chipNum = 10
                NumberSelectorViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.number_selector_super_loto, parent, false))
            }
            4, 10 -> { // On Numara
                chipNum = 8
                NumberSelectorViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.number_selector_on_numara, parent, false))
            }
            else -> { // Sans Topu
                chipNum = 7
                NumberSelectorViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.number_selector_sans_topu, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: NumberSelectorViewHolder, position: Int) {
        holder.numberGroup.tag = "chipGroup$position" // Assign tag to ChipGroup to refer to it easier while checking selected numbers
        holder.numberGroup2.tag = "chipGroupTwo$position"
        // Use width and set width of single number chip to fit 9 items into a single row (OnGlobalLayoutListener works for only the first tab)
        holder.view.post { // Without post it does not always update the layout
            val totalWidth = holder.numberGroup.measuredWidth
            val singleWidth = holder.numberGroup.getChildAt(0).measuredWidth
            val targetWidth = totalWidth / chipNum
            // Overlapping touch areas
            if (singleWidth > targetWidth)
                holder.numberGroup.chipSpacingHorizontal = ((totalWidth - singleWidth) / (chipNum - 1)) - singleWidth
            // Non-overlapping touch areas
            else
                holder.numberGroup.chipSpacingHorizontal = (totalWidth - chipNum * singleWidth) / (chipNum - 1)
            // Last number for sans topu
            if (chipNum == 7) {
                holder.numberGroup2.visibility = View.VISIBLE
                if (singleWidth > targetWidth)
                    holder.numberGroup2.chipSpacingHorizontal = ((totalWidth - singleWidth) / (chipNum - 1)) - singleWidth
                else
                    holder.numberGroup2.chipSpacingHorizontal = (totalWidth - chipNum * singleWidth) / (chipNum - 1)
            }
        }
    }

    class NumberSelectorViewHolder(val view: View): RecyclerView.ViewHolder(view) {
        val numberGroup: ChipGroup = view.findViewById(R.id.chip_group)
        val numberGroup2: ChipGroup = view.findViewById(R.id.chip_group_2)
    }
}

package com.creadeep.kazanio

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * Variation of NoFilterArrayAdapter with only first 4 items enabled.
 */
class NoFilterDisabledItemsArrayAdapter<String>(context: Context, layout: Int, var values: Array<String>) :
        ArrayAdapter<String>(context, layout, values) {
    private val filterThatDoesNothing = object: Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            results.values = values
            results.count = values.size
            return results
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            val li = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            // Use layouts with different text color to differentiate disabled items
            view = if (position < 4)
                li.inflate(R.layout.dropdown_menu_popup_item, parent, false)
            else {
                li.inflate(R.layout.dropdown_menu_popup_item_disabled, parent, false)
            }
            return super.getView(position, view, parent)
        }
        return super.getView(position, convertView, parent)
    }

    override fun isEnabled(position: Int): Boolean {
        if (position > 3)
            return false
        return super.isEnabled(position)
    }

    override fun areAllItemsEnabled(): Boolean {
        return false
    }

    override fun getFilter(): Filter {
        return filterThatDoesNothing
    }
}

package com.example.anen.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import com.example.anen.R
import com.example.anen.data.AppInfo

class AppSelectionAdapter(
    private val context: Context,
    private val originalList: List<AppInfo>,
    initialSelectedPackages: Set<String>
) : BaseAdapter(), Filterable {

    private var filteredList: List<AppInfo> = originalList
    private val selectedPackages: MutableSet<String> = initialSelectedPackages.toMutableSet()

    override fun getCount(): Int = filteredList.size
    override fun getItem(position: Int): Any = filteredList[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app_select, parent, false)
        val appInfo = filteredList[position]

        val ivIcon = view.findViewById<ImageView>(R.id.ivAppIcon)
        val tvName = view.findViewById<TextView>(R.id.tvAppName)
        val cbSelected = view.findViewById<CheckBox>(R.id.cbAppSelected)

        ivIcon.setImageDrawable(appInfo.icon)
        tvName.text = appInfo.name
        cbSelected.isChecked = selectedPackages.contains(appInfo.packageName)

        // Handle row click to toggle checkbox
        view.setOnClickListener {
            if (selectedPackages.contains(appInfo.packageName)) {
                selectedPackages.remove(appInfo.packageName)
                cbSelected.isChecked = false
            } else {
                selectedPackages.add(appInfo.packageName)
                cbSelected.isChecked = true
            }
        }

        return view
    }

    fun getSelectedPackages(): Set<String> {
        return selectedPackages
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase()?.trim() ?: ""
                val results = FilterResults()

                if (query.isEmpty()) {
                    results.values = originalList
                    results.count = originalList.size
                } else {
                    val filtered = originalList.filter { it.name.lowercase().contains(query) }
                    results.values = filtered
                    results.count = filtered.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<AppInfo> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}
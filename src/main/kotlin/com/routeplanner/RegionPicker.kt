package com.routeplanner

import android.content.Context
import androidx.appcompat.app.AlertDialog

/** Drill-down region chooser: countries first, then their sub-regions. */
object RegionPicker {

    fun show(context: Context, parent: Region? = null, onPicked: (Region) -> Unit) {
        val entries = ArrayList<Pair<String, () -> Unit>>()

        if (parent != null) {
            entries.add("◀ Back" to {
                show(context, parent.parentId?.let { RegionManager.getRegionById(it) }, onPicked)
            })
            if (parent.url != null) {
                entries.add("Entire ${parent.name}" to { onPicked(parent) })
            }
        }
        for (region in RegionManager.childrenOf(parent?.id)) {
            if (RegionManager.hasChildren(region)) {
                entries.add("${region.name} ›" to { show(context, region, onPicked) })
            } else {
                val size = region.sizeApprox?.let { " ($it)" } ?: ""
                entries.add("${region.name}$size" to { onPicked(region) })
            }
        }

        AlertDialog.Builder(context)
            .setTitle(parent?.name ?: "Select country")
            .setItems(entries.map { it.first }.toTypedArray()) { _, which -> entries[which].second() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

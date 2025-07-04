package com.skyblue.skybluecontacts

import androidx.recyclerview.widget.DiffUtil
import com.skyblue.skybluecontacts.model.TrashContact

class TrashDiffCallback(
    private val oldList: List<TrashContact>,
    private val newList: List<TrashContact>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Replace with your unique ID check
        return oldList[oldItemPosition].trashId == newList[newItemPosition].trashId
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

package com.example.sasalele.data

data class HeaderTextBox(val text:String,val left:Int,val top:Int,val right:Int,val bottom:Int)

/** Coordinates are in decoded image pixels, independent of device density and orientation. */
object HeaderTitleSelector {
    fun select(boxes:List<HeaderTextBox>,width:Int,statusBottom:Int):String {
        if(width<=0) return ""
        val choices=boxes.filter {
            val center=(it.left+it.right)/2f
            it.top>=statusBottom && center in width*0.25f..width*0.75f &&
                it.left>=width*0.12f && it.right<=width*0.90f &&
                HeaderNameRules.candidate(listOf(it.text)).isNotBlank()
        }
        // Competing text on separate rows is ambiguous; never guess from OCR reading order.
        if(choices.size!=1) return ""
        return HeaderNameRules.candidate(listOf(choices.single().text))
    }
}

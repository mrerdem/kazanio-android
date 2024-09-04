package com.creadeep.kazanio

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * To prevent second tap deselect button (we want to keep a single button selected all the time).
 * Can be removed after material-1.3.0 becomes stable.
 */
class CustomMaterialButton: MaterialButton {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun toggle() {
        if (!isChecked)
            super.toggle()
    }
}

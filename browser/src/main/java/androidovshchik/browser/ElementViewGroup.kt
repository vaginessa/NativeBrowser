package androidovshchik.browser

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import org.jsoup.nodes.Element

class ElementViewGroup @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    fun smth(element: Element) {
        element
            .select("style")
            .forEach {
                it.data()
            }
    }
}
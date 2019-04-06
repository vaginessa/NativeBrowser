package androidovshchik.browser

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import org.jsoup.nodes.Element
import timber.log.Timber

class ElementViewGroup @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    fun init(element: Element) {
        element.allElements.forEach {
            Timber.d(it.tagName())
        }
    }
}
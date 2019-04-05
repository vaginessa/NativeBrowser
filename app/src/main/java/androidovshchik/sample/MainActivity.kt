package androidovshchik.sample

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        webView.setWebViewClient {
            shouldOverrideUrlLoading {
                input.setText(it)
                return@shouldOverrideUrlLoading true
            }
        }
        webView.loadUrl("https://yandex.ru")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_reload -> {
                webView.reload()
            }
            R.id.action_google -> {
                webView.loadUrl("https://google.ru")
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

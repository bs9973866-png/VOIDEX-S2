package com.voidex.s2

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val skins = mutableListOf<String>()
    private lateinit var list: LinearLayout
    private val pickSkin = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 8, 18)
        window.navigationBarColor = Color.rgb(10, 8, 18)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 24)
            setBackgroundColor(Color.rgb(10, 8, 18))
        }
        val title = TextView(this).apply {
            text = "VOIDEX S2"
            textSize = 29f
            setTextColor(Color.rgb(255, 65, 100))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title)
        root.addView(label("EXTERNAL COMPANION  •  ANDROID"))
        root.addView(label("Локальная коллекция скинов и тренировочные инструменты"))
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(action("СКИНЫ") { showSkins() }, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(action("ТРЕНИРОВКА") { showTraining() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)
        root.addView(action("+ ИМПОРТИРОВАТЬ PNG СКИН") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(i, pickSkin)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showSkins()
    }

    private fun showSkins() {
        list.removeAllViews()
        list.addView(label("МОЯ ЛОКАЛЬНАЯ КОЛЛЕКЦИЯ"))
        if (skins.isEmpty()) list.addView(label("Пока пусто. Импортируй PNG-файлы, чтобы создать каталог локальных превью."))
        skins.forEach { name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 12, 12, 12)
                setBackgroundColor(Color.rgb(27, 21, 39))
            }
            row.addView(label(name), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(action("ВЫБРАТЬ") {
                getSharedPreferences("voidex", MODE_PRIVATE).edit().putString("selected_skin", name).apply()
                Toast.makeText(this, "Выбрано локальное превью: $name", Toast.LENGTH_SHORT).show()
            })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
        list.addView(label("Выбор меняет только локальную запись приложения — игровой инвентарь и модель персонажа не затрагиваются."))
    }

    private fun showTraining() {
        list.removeAllViews()
        list.addView(label("ТРЕНИРОВОЧНЫЙ РЕЖИМ"))
        list.addView(label("Здесь появятся таймеры реакции, упражнения на трекинг и настройка чувствительности."))
        list.addView(label("Демонстрационные маркеры будут работать только на тестовом экране, не определяя игроков в матче."))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(220, 212, 235))
        setPadding(0, 12, 0, 12)
    }

    private fun action(value: String, run: () -> Unit) = Button(this).apply {
        text = value
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(115, 35, 65))
        setOnClickListener { run() }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickSkin && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name = queryName(uri) ?: "skin_${skins.size + 1}.png"
                skins.add(name)
                showSkins()
            }
        }
    }

    private fun queryName(uri: Uri): String? = contentResolver.query(uri, null, null, null, null)?.use { c ->
        val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && col >= 0) c.getString(col) else null
    }
}

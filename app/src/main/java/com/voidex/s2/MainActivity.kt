package com.voidex.s2

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private data class Skin(val id: String, val name: String, val uri: String)

    private val skins = mutableListOf<Skin>()
    private lateinit var content: LinearLayout
    private var currentTab = "skins"
    private val pickSkin = 41
    private val prefs by lazy { getSharedPreferences("voidex_s2", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 7, 17)
        window.navigationBarColor = Color.rgb(9, 7, 17)
        loadSkins()
        buildShell()
        showSkins()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
            setBackgroundColor(Color.rgb(9, 7, 17))
        }
        root.addView(TextView(this).apply {
            text = "VOIDEX S2"
            textSize = 29f
            setTextColor(Color.rgb(255, 70, 125))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(label("COSMETIC LAB  •  ANDROID COMPANION"))
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(action("МОИ СКИНЫ") { currentTab = "skins"; showSkins() },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        tabs.addView(action("ТРЕНИРОВКА") { currentTab = "training"; showTraining() },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(tabs)
        root.addView(action("ЗАПУСТИТЬ OVERLAY") {
            startOverlayIfAllowed(launchGame = false)
        })
        root.addView(action("ЗАПУСТИТЬ STANDOFF 2 + OVERLAY") {
            startOverlayIfAllowed(launchGame = true)
        })
        root.addView(label("Кнопка запускает внешний оверлей и открывает Standoff 2. Оверлей не получает скрытые координаты игроков; маркеры появятся только при подключении разрешённого источника данных."))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun startOverlayIfAllowed(launchGame: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)) {
            val permissionIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(permissionIntent)
            Toast.makeText(
                this,
                "Разреши отображение поверх других приложений, вернись в VOIDEX и нажми кнопку ещё раз.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val serviceIntent = Intent(this, FloatingPanelService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (error: Exception) {
            Toast.makeText(this, "Не удалось запустить Overlay: ${error.message}", Toast.LENGTH_LONG).show()
            return
        }

        if (!launchGame) {
            Toast.makeText(this, "VOIDEX Overlay запущен", Toast.LENGTH_SHORT).show()
            return
        }

        val gameIntent = packageManager.getLaunchIntentForPackage("com.axlebolt.standoff2")
        if (gameIntent == null) {
            Toast.makeText(this, "Standoff 2 не найдена. Установи игру и попробуй снова.", Toast.LENGTH_LONG).show()
            return
        }
        gameIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(gameIntent)
    }

    private fun showSkins() {
        currentTab = "skins"
        content.removeAllViews()
        content.addView(label("ЛОКАЛЬНЫЙ КАТАЛОГ"))
        content.addView(label("Импортируй изображение скина или оружия, чтобы сохранить его в VOIDEX и открыть крупное превью."))
        content.addView(action("+ ДОБАВИТЬ ИЗОБРАЖЕНИЕ") { openPicker() })
        content.addView(label("МОЯ КОЛЛЕКЦИЯ  •  ${skins.size}"))
        if (skins.isEmpty()) {
            content.addView(label("Пока пусто. Нажми «Добавить изображение» и выбери PNG или JPG."))
        }
        skins.forEach { skin ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                setBackgroundColor(Color.rgb(27, 20, 39))
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(Uri.parse(skin.uri))
            }
            card.addView(thumb)
            val name = TextView(this).apply {
                text = skin.name
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding(dp(12), 0, dp(6), 0)
            }
            card.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(action("ОТКРЫТЬ") { showPreview(skin) })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        content.addView(label("ВАЖНО: это рабочий локальный просмотрщик. Он не подменяет текстуры внутри Standoff 2 и не меняет то, что видят другие игроки."))
    }

    private fun showPreview(skin: Skin) {
        content.removeAllViews()
        content.addView(action("← НАЗАД К КОЛЛЕКЦИИ") { showSkins() })
        content.addView(label(skin.name))
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(20, 16, 29))
            setImageURI(Uri.parse(skin.uri))
            contentDescription = "Локальное превью: ${skin.name}"
        }
        content.addView(image, LinearLayout.LayoutParams(-1, dp(360)).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        })
        content.addView(action("ВЫБРАТЬ ДЛЯ ПРЕВЬЮ") {
            prefs.edit().putString("selected_skin_id", skin.id).apply()
            Toast.makeText(this, "Выбрано локальное превью: ${skin.name}", Toast.LENGTH_SHORT).show()
        })
        content.addView(action("УДАЛИТЬ ИЗ КОЛЛЕКЦИИ") {
            skins.removeAll { it.id == skin.id }
            saveSkins()
            showSkins()
        })
        content.addView(label("Выбор сохраняется в приложении. Визуальная замена модели/оружия непосредственно в матче не поддерживается."))
    }

    private fun showTraining() {
        currentTab = "training"
        content.removeAllViews()
        content.addView(label("ТРЕНИРОВОЧНАЯ ЛАБОРАТОРИЯ"))
        content.addView(label("Следующий безопасный модуль — автономные упражнения на реакцию, трекинг и контроль чувствительности."))
        content.addView(label("Тренировочные мишени будут отображаться только внутри VOIDEX S2, без чтения изображения игры и обнаружения игроков в матче."))
        content.addView(action("ОТКРЫТЬ ТЕСТ РЕАКЦИИ") { showReactionTest() })
    }

    private fun showReactionTest() {
        content.removeAllViews()
        content.addView(action("← НАЗАД") { showTraining() })
        content.addView(label("ТЕСТ РЕАКЦИИ"))
        val result = TextView(this).apply {
            text = "Нажми «СТАРТ», затем нажми на кнопку, когда она станет зелёной."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, dp(18))
        }
        content.addView(result)
        val target = action("СТАРТ") {
            result.text = "ТЕСТОВАЯ МИШЕНЬ — нажми, чтобы зафиксировать реакцию. Это демонстрация интерфейса, не измерение точного времени."
            targetButtonStatePlaceholder()
        }
        content.addView(target)
    }

    private fun targetButtonStatePlaceholder() {
        Toast.makeText(this, "Тренировочный экран готов к следующему этапу", Toast.LENGTH_SHORT).show()
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, pickSkin)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickSkin || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val mime = contentResolver.getType(uri) ?: ""
        if (!mime.startsWith("image/")) {
            Toast.makeText(this, "Выбери файл изображения", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some document providers issue a temporary URI grant only.
        }
        val name = queryName(uri) ?: "image_${skins.size + 1}"
        if (skins.none { it.uri == uri.toString() }) {
            skins.add(Skin(System.currentTimeMillis().toString(), name, uri.toString()))
            saveSkins()
        }
        showSkins()
    }

    private fun queryName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }

    private fun loadSkins() {
        skins.clear()
        val raw = prefs.getString("skins_json", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val uri = item.optString("uri")
                if (uri.isNotBlank()) skins.add(Skin(item.optString("id"), item.optString("name"), uri))
            }
        } catch (_: Exception) {
            prefs.edit().remove("skins_json").apply()
        }
    }

    private fun saveSkins() {
        val array = JSONArray()
        skins.forEach { skin ->
            array.put(JSONObject().put("id", skin.id).put("name", skin.name).put("uri", skin.uri))
        }
        prefs.edit().putString("skins_json", array.toString()).apply()
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.rgb(218, 210, 232))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun action(value: String, run: () -> Unit) = Button(this).apply {
        text = value
        textSize = 11f
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(120, 34, 75))
        setOnClickListener { run() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

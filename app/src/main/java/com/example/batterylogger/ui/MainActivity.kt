package com.example.batterylogger.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.batterylogger.io.StorageDirs
import com.example.batterylogger.service.LoggerService
import com.example.batterylogger.session.Mechanism
import com.example.batterylogger.session.SessionStore

/**
 * Минимальный UI без AppCompat и XML-разметки. Никаких таймеров обновления:
 * статус перечитывается только в onResume и после нажатий.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val group = RadioGroup(this)
        val rbA = RadioButton(this).apply { id = View.generateViewId(); text = "A: Handler (без будильников)" }
        val rbB = RadioButton(this).apply { id = View.generateViewId(); text = "B: AlarmManager, exact + allowWhileIdle" }
        group.addView(rbA)
        group.addView(rbB)
        group.check(rbA.id)
        root.addView(group)

        root.addView(Button(this).apply {
            text = "Старт"
            setOnClickListener {
                start(if (group.checkedRadioButtonId == rbB.id) Mechanism.ALARM else Mechanism.HANDLER)
            }
        })
        root.addView(Button(this).apply {
            text = "Стоп"
            setOnClickListener {
                startService(Intent(this@MainActivity, LoggerService::class.java).setAction(LoggerService.ACTION_STOP))
                status.postDelayed({ refresh() }, 500)
            }
        })
        root.addView(Button(this).apply {
            text = "Поделиться последней сессией"
            setOnClickListener {
                val intent = ShareHelper.buildIntent(this@MainActivity)
                if (intent == null) {
                    Toast.makeText(this@MainActivity, "Пока нечего отправлять", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(intent)
                }
            }
        })
        if (Build.VERSION.SDK_INT >= 31) {
            root.addView(Button(this).apply {
                text = "Разрешение на точные будильники (для B)"
                setOnClickListener {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                }
            })
        }

        status = TextView(this).apply { setTextIsSelectable(true); setPadding(0, pad, 0, 0) }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })

        // Android 13+: без разрешения FGS работает, но уведомление видно только в Task Manager.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        root.setPadding(0, 512, 0, 0)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun start(m: Mechanism) {
        // Плечо B по определению — точные будильники. Без разрешения не стартуем,
        // чтобы не получить молча другое условие эксперимента.
        if (m == Mechanism.ALARM && Build.VERSION.SDK_INT >= 31 &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            Toast.makeText(this, "Сначала выдайте разрешение на точные будильники", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(
            Intent(this, LoggerService::class.java)
                .setAction(LoggerService.ACTION_START)
                .putExtra(LoggerService.EXTRA_MECHANISM, m.name)
        )
        status.postDelayed({ refresh() }, 500)
    }

    private fun refresh() {
        val s = SessionStore.load(this)
        val root = StorageDirs.root(this)
        status.text = buildString {
            if (s == null) {
                append("Сессия не активна.\n")
            } else {
                append("Сессия: ${s.id}\nМеханизм: ${s.mechanism}\n")
                append("Сервис жив в этом процессе: ${LoggerService.alive}\n")
                if (!LoggerService.alive) append("(процесс перезапускался — см. process_exits.csv)\n")
                append("\nФайлы:\n${s.dir.absolutePath}\n")
            }
            append("\nЗабрать всё:\nadb pull ${root.absolutePath}/ .\n")
            if (Build.VERSION.SDK_INT >= 31) {
                append("\ncanScheduleExactAlarms: ${getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}\n")
            }
        }
    }
}

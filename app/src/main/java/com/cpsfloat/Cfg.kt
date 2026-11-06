package com.cpsfloat

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.view.WindowManager

const val SLOTS = 2

object Modes {
    const val FULL = 0    // 全屏点击统计（Shizuku）
    const val REGION = 1  // 指定区域统计（Shizuku）
    const val COVER = 2   // 区域覆盖统计（无需 Shizuku，会拦截该区域点击）
}

data class SlotCfg(
    val enabled: Boolean,
    val mode: Int,
    val rx: Int, val ry: Int, val rw: Int, val rh: Int,   // 点击区域（屏幕像素）
    val x: Int, val y: Int,                                // CPS 显示位置
    val sizeSp: Int,
    val textColor: Int,
    val bgColor: Int,
    val radiusDp: Int,
    val template: String,
    val font: String,
    val bold: Boolean,
    val hideText: Boolean,
    val idleBgOnly: Boolean,
    val locked: Boolean
) {
    val hasRegion get() = rw > 0 && rh > 0
}

/** 所有设置改动即时写入 SharedPreferences，服务监听变化后自动同步，无需“保存并应用”。 */
object Cfg {
    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("cps_cfg", Context.MODE_PRIVATE)

    private fun k(i: Int, n: String) = "s${i}_$n"

    // ---- 横/竖屏分别保存位置与区域；某个方向没保存过时，按比例从另一个方向换算 ----
    @Suppress("DEPRECATION")
    private fun real(c: Context): IntArray {
        val wm = c.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    fun isLandscape(c: Context): Boolean { val r = real(c); return r[0] > r[1] }

    private fun sfx(land: Boolean) = if (land) "_l" else ""

    /** 从“另一个方向”换算到“当前方向”的 x、y 缩放系数 */
    private fun scale(c: Context, land: Boolean): FloatArray {
        val r = real(c)
        val a = minOf(r[0], r[1]).toFloat()
        val b = maxOf(r[0], r[1]).toFloat()
        return if (land) floatArrayOf(b / a, a / b) else floatArrayOf(a / b, b / a)
    }

    private fun pos(p: SharedPreferences, c: Context, i: Int, dx: Int, dy: Int, land: Boolean): IntArray {
        val s = sfx(land); val o = sfx(!land)
        if (p.contains(k(i, "x$s"))) return intArrayOf(p.getInt(k(i, "x$s"), dx), p.getInt(k(i, "y$s"), dy))
        if (p.contains(k(i, "x$o"))) {
            val f = scale(c, land)
            return intArrayOf((p.getInt(k(i, "x$o"), dx) * f[0]).toInt(), (p.getInt(k(i, "y$o"), dy) * f[1]).toInt())
        }
        return intArrayOf(dx, dy)
    }

    private fun region(p: SharedPreferences, c: Context, i: Int, land: Boolean): IntArray {
        val s = sfx(land); val o = sfx(!land)
        if (p.contains(k(i, "rw$s"))) return intArrayOf(
            p.getInt(k(i, "rx$s"), 0), p.getInt(k(i, "ry$s"), 0),
            p.getInt(k(i, "rw$s"), 0), p.getInt(k(i, "rh$s"), 0))
        if (p.contains(k(i, "rw$o"))) {
            val f = scale(c, land)
            return intArrayOf(
                (p.getInt(k(i, "rx$o"), 0) * f[0]).toInt(), (p.getInt(k(i, "ry$o"), 0) * f[1]).toInt(),
                (p.getInt(k(i, "rw$o"), 0) * f[0]).toInt(), (p.getInt(k(i, "rh$o"), 0) * f[1]).toInt())
        }
        return intArrayOf(0, 0, 0, 0)
    }

    fun load(c: Context, i: Int): SlotCfg {
        val p = prefs(c)
        fun gi(n: String, d: Int) = p.getInt(k(i, n), d)
        fun gb(n: String, d: Boolean) = p.getBoolean(k(i, n), d)
        val land = isLandscape(c)
        val ps = pos(p, c, i, 40, 200 + i * 180, land)
        val rg = region(p, c, i, land)
        return SlotCfg(
            enabled = gb("enabled", false),
            mode = gi("mode", Modes.FULL),
            rx = rg[0], ry = rg[1], rw = rg[2], rh = rg[3],
            x = ps[0], y = ps[1],
            sizeSp = gi("size", 22),
            textColor = gi("textColor", 0xFFFFFFFF.toInt()),
            bgColor = gi("bgColor", 0xAA000000.toInt()),
            radiusDp = gi("radius", 12),
            template = p.getString(k(i, "template"), "{cps} CPS") ?: "{cps} CPS",
            font = p.getString(k(i, "font"), "sans-serif") ?: "sans-serif",
            bold = gb("bold", true),
            hideText = gb("hideText", false),
            idleBgOnly = gb("idleBgOnly", false),
            locked = gb("locked", false)
        )
    }

    fun putInt(c: Context, i: Int, n: String, v: Int) = prefs(c).edit().putInt(k(i, n), v).apply()
    fun putBool(c: Context, i: Int, n: String, v: Boolean) = prefs(c).edit().putBoolean(k(i, n), v).apply()
    fun putStr(c: Context, i: Int, n: String, v: String) = prefs(c).edit().putString(k(i, n), v).apply()

    /** 保存到当前屏幕方向（横/竖屏各存一份） */
    fun putRegion(c: Context, i: Int, x: Int, y: Int, w: Int, h: Int) {
        val s = sfx(isLandscape(c))
        prefs(c).edit()
            .putInt(k(i, "rx$s"), x).putInt(k(i, "ry$s"), y)
            .putInt(k(i, "rw$s"), w).putInt(k(i, "rh$s"), h).apply()
    }

    fun putPos(c: Context, i: Int, x: Int, y: Int) {
        val s = sfx(isLandscape(c))
        prefs(c).edit().putInt(k(i, "x$s"), x).putInt(k(i, "y$s"), y).apply()
    }

    fun anyEnabled(c: Context) = (0 until SLOTS).any { load(c, it).enabled }
}

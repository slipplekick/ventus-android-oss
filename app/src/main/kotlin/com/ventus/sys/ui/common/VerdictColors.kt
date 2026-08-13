package com.ventus.sys.ui.common

import androidx.compose.ui.graphics.Color
import com.ventus.sys.domain.model.Verdict

/** Matches app.js's verdict palette exactly (style.css's --resonance/--quincy/--chrome/--clash/--bankai). */
fun verdictColor(verdict: Verdict): Color =
    when (verdict) {
        Verdict.CORE -> Color(0xFFD4AF37)
        Verdict.ALIGNED -> Color(0xFF4DB8FF)
        Verdict.FRINGE -> Color(0xFFF5E642)
        Verdict.OUTLIER -> Color(0xFFFF7A1A)
        Verdict.NO_MATCH, Verdict.NO_SIGNAL -> Color(0xFFFF4C2B)
    }

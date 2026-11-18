package com.dieselalert.app

import android.content.Context

object Maint {
    /** Avisa (notificación + otros celulares) cuando un mantenimiento está por vencer o vencido. Una vez por nivel. */
    fun check(ctx: Context) {
        val st = Store(ctx)
        if (st.dashKm() == null) return
        val warn = st.warnKm
        val items = st.maintItems()
        var changed = false
        val out = items.mapIndexed { idx, m ->
            val rem = st.maintRemaining(m)
            if (rem == null) {
                m
            } else {
                val lvl = when {
                    rem <= 0 -> 2
                    rem <= warn -> 1
                    else -> 0
                }
                if (lvl > m.level) {
                    val text = Msg.maint(st, m, rem)
                    Notifs.ensureChannels(ctx)
                    Notifs.post(
                        ctx, 50 + idx,
                        if (lvl == 2) "Mantenimiento vencido" else "Mantenimiento próximo",
                        text.substringAfter('\n'), Notifs.CH_ALERT
                    )
                    if (st.relayAlerts) {
                        Relay.send(
                            ctx, if (lvl == 2) "Mantenimiento vencido" else "Mantenimiento próximo",
                            text, if (lvl == 2) 4 else 3, "wrench"
                        )
                    }
                    changed = true
                    m.copy(level = lvl)
                } else if (lvl < m.level) {
                    changed = true
                    m.copy(level = lvl)
                } else m
            }
        }
        if (changed) st.saveMaint(out)
    }
}

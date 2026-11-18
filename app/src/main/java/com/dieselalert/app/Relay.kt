package com.dieselalert.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Envía los avisos a otros celulares con ntfy (https://ntfy.sh). El otro celular instala la app
 * gratuita "ntfy" y se suscribe al mismo tema. Si no hay internet, el aviso queda en cola y se reintenta.
 */
object Relay {
    private val ex = Executors.newSingleThreadExecutor()

    fun newTopic(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val r = SecureRandom()
        return "diesel-" + (1..16).map { chars[r.nextInt(chars.length)] }.joinToString("")
    }

    fun send(ctx: Context, title: String, message: String, priority: Int = 3, tag: String = "fuelpump") {
        val app = ctx.applicationContext
        ex.execute {
            val st = Store(app)
            if (!st.relayOn || st.relayTopic.isBlank()) return@execute
            val arr = JSONArray(st.relayQueue)
            arr.put(
                JSONObject().put("title", title).put("message", message)
                    .put("priority", priority).put("tag", tag)
            )
            // máximo 20 pendientes
            val keep = JSONArray()
            for (i in maxOf(0, arr.length() - 20) until arr.length()) keep.put(arr.get(i))
            st.relayQueue = keep.toString()
            doFlush(st)
        }
    }

    fun flush(ctx: Context) {
        val app = ctx.applicationContext
        ex.execute {
            val st = Store(app)
            if (st.relayOn && st.relayTopic.isNotBlank()) doFlush(st)
        }
    }

    private fun doFlush(st: Store) {
        var arr = JSONArray(st.relayQueue)
        while (arr.length() > 0) {
            val o = arr.getJSONObject(0)
            val err = post(st, o)
            if (err != null) {
                st.relayStatus = "Error: $err (se reintentará)"
                return
            }
            val rest = JSONArray()
            for (i in 1 until arr.length()) rest.put(arr.get(i))
            arr = rest
            st.relayQueue = arr.toString()
            st.relayStatus = "Último envío correcto: " + SimpleDateFormat("d MMM HH:mm", Locale("es")).format(Date())
        }
    }

    /** Devuelve null si salió bien, o el texto del error. */
    private fun post(st: Store, o: JSONObject): String? {
        return try {
            val body = JSONObject()
                .put("topic", st.relayTopic)
                .put("title", o.getString("title"))
                .put("message", o.getString("message"))
                .put("priority", o.getInt("priority"))
                .put("tags", JSONArray().put(o.getString("tag")))
                .toString()
            val c = URL(st.relayServer.trimEnd('/') + "/").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) null else "servidor respondió $code"
        } catch (e: Exception) {
            e.message ?: "sin conexión"
        }
    }
}

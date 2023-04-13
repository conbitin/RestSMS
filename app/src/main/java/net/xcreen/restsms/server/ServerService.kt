package net.xcreen.restsms.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.SparseIntArray
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.squareup.okhttp.*
import net.xcreen.restsms.AppContext
import net.xcreen.restsms.DEFAULT_FEED_URL
import net.xcreen.restsms.DEFAULT_POSTBACK_URL
import net.xcreen.restsms.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileDescriptor
import java.io.PrintWriter
import java.net.BindException

class ServerService : Service() {
    private val SERVER_SERVICE_ID = 10000
    private val NOTIFICATION_CHANNEL_ID = "sms_server_notification_channel"
    private val NOTIFICATION_CHANNEL_NAME = "Sms-Server Service"
    private var appContext: AppContext? = null

    companion object {
        var isRunning = false
        const val START_ACTION = "start"
        const val STOP_ACTION = "stop"
        var feedUrl: String? = DEFAULT_FEED_URL
        var postbackUrl: String? = DEFAULT_POSTBACK_URL
    }

    override fun onCreate() {
        appContext = application as AppContext
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val intentAction = intent.action
        if (intentAction != null && intentAction == START_ACTION) {
            //Check if Server is already running or in process
            if (!appContext?.smsServer?.isRunning!! && !appContext?.smsServer?.isStopping!!) {
                startService()
            }
        } else if (intentAction != null && intentAction == STOP_ACTION) {
            stopForeground(true)
            stopSelf()
        }
        return START_STICKY
    }

    /**
     * Start Server and Service in Foreground
     */
    private fun startService() {
        //Set Port
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val serverPort = sharedPref.getInt("server_port", 8080)
        feedUrl = sharedPref.getString("feed_url", DEFAULT_FEED_URL)
        postbackUrl =
            sharedPref.getString("postback_url", DEFAULT_POSTBACK_URL)
//        appContext?.smsServer?.port = serverPort

        //Set Stop-Button
        val stopIntent = Intent(this, ServerService::class.java)
        stopIntent.action = STOP_ACTION
        var pendingStopIntent: PendingIntent
        if (Build.VERSION.SDK_INT >= 31) {
            pendingStopIntent =
                PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_MUTABLE)
        } else {
            pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, 0)
        }

        //Setup Notification-Channel
        if (Build.VERSION.SDK_INT >= 26) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
        //Create Notification
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(getColor(R.color.colorPrimary))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(
                R.drawable.notification_stop,
                getString(R.string.stop_server),
                pendingStopIntent
            )
            .build()

        //Start Serve in new Thread
        Thread(Runnable {
            try {
                //Start Server
                val cacheDir = cacheDir.absolutePath
                appContext?.smsServer?.start(cacheDir)
            } catch (bindEx: BindException) {
                //Failed to bind on the given port
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    Toast.makeText(
                        applicationContext,
                        resources.getText(R.string.server_failed_bindex),
                        Toast.LENGTH_LONG
                    ).show()
                }
                appContext?.smsServer?.serverLogging!!.log(
                    "error",
                    "Server cant start up on this port (Bind-Exception)!"
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    Toast.makeText(
                        applicationContext,
                        resources.getText(R.string.server_failed_to_start),
                        Toast.LENGTH_LONG
                    ).show()
                }
                appContext?.smsServer?.serverLogging!!.log("error", "Failed to start up server!")
            } finally {
                //Stop Service
                val serverIntent = Intent(applicationContext, ServerService::class.java)
                serverIntent.action = STOP_ACTION
                startService(serverIntent)
            }
        }).start()
        //Set in Foreground
        startForeground(SERVER_SERVICE_ID, notification)
        startHandler()
    }

    override fun onDestroy() {
        Log.i("ServerService", "onDestroy()")
        try {
            if (appContext?.smsServer?.isRunning!! && !appContext?.smsServer?.isStopping!!) {
                appContext?.smsServer?.stop()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private val handlerThread = HandlerThread("HandlerThread")
    private var handler: H? = null

    private fun startHandler() {
        if (!handlerThread.isAlive) {
            handlerThread.start()
        }
        handler = H(this, handlerThread.looper)
        handler?.sendEmptyMessage(1)
    }

    internal class H(private val context: Context, looper: Looper) : Handler(looper) {
        private val okHttpClient = OkHttpClient()
        private val sentIds = SparseIntArray()

        override fun handleMessage(msg: Message) {
            if (msg.what == 1) {
                getDataFromServer()
                if (isRunning) {
                    sendEmptyMessageDelayed(1, 1000)
                }
            }
        }

        private fun getDataFromServer() {
            try {
                val request = Request.Builder().url(feedUrl).build()
                val response = okHttpClient.newCall(request).execute()
                val jsonString = response.body().string()
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    sendMessage(jsonObject)
                }
            } catch (e: Exception) {
                Log.e("ServerService", "getDataFromServer failed", e)
            }
        }

        private fun sendMessage(jsonObject: JSONObject) {
            val message = jsonObject.getString("message")
            val phoneNumber = jsonObject.getString("phoneno")
            val id = jsonObject.getInt("id")
            if (sentIds.get(id) == 1) {
                return
            }
            val url = HttpUrl.Builder()
                .scheme("http")
                .host("127.0.0.1")
                .port(8080)
                .addQueryParameter("message", message)
                .addQueryParameter("phoneno", phoneNumber)
                .addQueryParameter("id", id.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), ""))
                .build()
            val response = okHttpClient.newCall(request).execute()
            sentIds.put(id, 1)
            postback(response.body().string())
            postDelayed({
                deleteMessage(message, phoneNumber)
            }, 2000)
        }

        private fun postback(response: String) {
            try {
                val jsonResponse = JSONObject(response)
                val id = jsonResponse.getInt("id")
                val success = jsonResponse.getBoolean("success")
                val url = HttpUrl.parse(postbackUrl)
                    .newBuilder()
                    .addQueryParameter("id", id.toString())
                    .addQueryParameter("status", if (success) "success" else "failed")
                    .build()
                Log.d("ServerService", "postback url: ${url.url()}")
                val request = Request.Builder()
                    .url(url)
                    .build()
                val postbackResponse = okHttpClient.newCall(request).execute()
                Log.d("ServerService", "postback response: $postbackResponse")
            } catch (e: Exception) {
                Log.e("ServerService", "send postback failed", e)
            }
        }

        private fun deleteMessage(message: String?, phoneNumber: String?) {
            val smsUri = Uri.parse("content://sms/sent")
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    smsUri,
                    arrayOf("_id", "address", "body"),
                    null, null, null
                )
                cursor ?: return
                if (cursor.moveToFirst()) {
                    do {
                        val id = cursor.getLong(0)
                        val address = cursor.getString(1)
                        val body = cursor.getString(2)
                        if (message?.equals(body) == true && phoneNumber?.equals(address) == true) {
                            val deletedRowNum = context.contentResolver.delete(
                                Uri.parse("content://sms/$id"),
                                null, null
                            )
                            if (deletedRowNum > 0) {
                                Log.d(
                                    "ServerService",
                                    "deleted phoneNumber=$phoneNumber, message=$message"
                                )
                            }
                        }
                    } while (cursor.moveToNext())
                }
            } catch (e: Exception) {
                Log.e("ServerService", "failed to delete message ", e)
            } finally {
                cursor?.close()
            }
        }
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter?, args: Array<out String>?) {

    }
}
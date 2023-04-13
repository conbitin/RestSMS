package net.xcreen.restsms.server

import android.os.Build
import android.telephony.SmsManager
import com.google.gson.GsonBuilder
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import net.xcreen.restsms.AppContext
import java.io.IOException
import javax.servlet.annotation.MultipartConfig
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet
@MultipartConfig
class SMSServlet(private val serverLogging: ServerLogging) : HttpServlet() {

    @Throws(IOException::class)
    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        serverLogging.log("info", "SMS-Servlet [" + request.method + "] Request /send From: " + request.remoteAddr)
        //Init Gson/PhoneNumberUtil
        val gsonBuilder = GsonBuilder()
        val gson = gsonBuilder.create()
        val phoneUtil = PhoneNumberUtil.getInstance()
        //Set Response
        response.contentType = "application/json"
        response.characterEncoding = "utf-8"
        val message = request.getParameter("message")
        val phoneno = request.getParameter("phoneno")
        val id = request.getParameter("id")
        //Check if post-parameters exists
        if (message == null || phoneno == null) {
            //Return Failing JSON
            serverLogging.log("error", "SMS-Servlet message and/or phoneno parameter is missing")
            response.writer.println(gson.toJson(SMSResponse(false, "message or phoneno parameter are missing!", id)))
            return
        }
        //Check if message is valid
        if (message.isEmpty()) {
            serverLogging.log("error", "SMS-Servlet Message is empty")
            //Return Failing JSON
            response.writer.println(gson.toJson(SMSResponse(false, "message is empty!", id)))
            return
        }
        //Check if phoneno is valid and parse it
        val phoneNumber: PhoneNumber
        try {
            phoneNumber = phoneUtil.parse(phoneno, null)
        }
        catch (ex: Exception) {
            serverLogging.log("error", "SMS-Servlet Failed to parse phoneno")
            ex.printStackTrace()
            //Return Failing JSON
            response.writer.println(gson.toJson(SMSResponse(false, "Invalid phoneno (make sure you include the + with Country-Code)!", id)))
            return
        }
        //Send SMS
        val smsManager = if(Build.VERSION.SDK_INT >= 31) {
            AppContext.appContext.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        val msgArray = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phoneUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL), null, msgArray, null, null)
        //Show Success message
        response.writer.println(gson.toJson(SMSResponse(true, null, id)))
        serverLogging.log("info", "SMS-Servlet Successful send!")
    }

}
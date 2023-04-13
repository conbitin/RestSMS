package net.xcreen.restsms.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import net.xcreen.restsms.DEFAULT_FEED_URL
import net.xcreen.restsms.DEFAULT_POSTBACK_URL
import net.xcreen.restsms.R

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)
        val currentContext: Context = context as Context
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(currentContext)
        val feedUrlEditText = rootView.findViewById<EditText>(R.id.settings_feed_url_edittext)
        val postbackEditText = rootView.findViewById<EditText>(R.id.settings_postback_url_edittext)
        val openBrowserCheckBox = rootView.findViewById<CheckBox>(R.id.settings_open_browser_checkbox)
        val disableLoggingCheckBox = rootView.findViewById<CheckBox>(R.id.settings_disable_logging_checkbox)
        val saveBtn = rootView.findViewById<Button>(R.id.settings_save_btn)
        saveBtn.setOnClickListener { v ->

            val editor = sharedPref.edit()
            //Save Feed URL
            var savedFeedUrl= false
            if (feedUrlEditText.text.isNotEmpty()) {
                val newFeedUrl = feedUrlEditText.text.toString()
                editor.putString("feed_url", newFeedUrl)
                editor.apply()
                savedFeedUrl = true
            } else {
                Toast.makeText(v.context, "Feed URL can not be empty", Toast.LENGTH_SHORT).show()
            }
            //Save Postback URL
            var savedPostbackUrl = false
            if (postbackEditText.text.isNotEmpty()) {
                val newPostbackUrl = postbackEditText.text.toString()
                editor.putString("postback_url", newPostbackUrl)
                editor.apply()
                savedPostbackUrl = true
            } else {
                Toast.makeText(v.context, "Postback URL can not be empty", Toast.LENGTH_SHORT).show()
            }
            //Save Open-Browser after Server-Start
            editor.putBoolean("open_browser_serverstart", openBrowserCheckBox.isChecked)
            editor.apply()
            //Save Disable-Logging-Option
            editor.putBoolean("disable_logging", disableLoggingCheckBox.isChecked)
            editor.apply()
            if (savedFeedUrl && savedPostbackUrl) {
                Toast.makeText(v.context, resources.getText(R.string.setting_saved), Toast.LENGTH_SHORT).show()
            }
        }
        //Set current Port
        feedUrlEditText.setText(sharedPref.getString("feed_url", DEFAULT_FEED_URL).toString())
        postbackEditText.setText(sharedPref.getString("postback_url", DEFAULT_POSTBACK_URL).toString())
        //Set current "Open-Browser after Server-Start"-Option
        if (sharedPref.getBoolean("open_browser_serverstart", false)) {
            openBrowserCheckBox.isChecked = true
        }
        //Set current "Disable Logging"-Option
        if (sharedPref.getBoolean("disable_logging", false)) {
            disableLoggingCheckBox.isChecked = true
        }
        return rootView
    }
}
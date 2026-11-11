package com.smartsense.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.smartsense.app.R
import timber.log.Timber

object FooterUtils {

    fun setupBmproFooter(footerView: View) {
        val context = footerView.context ?: return

        footerView.findViewById<TextView>(R.id.footer_landing_text)?.let { styleLandingLink(it) }

        val landingLink = footerView.findViewById<View>(R.id.footer_landing_link)
        landingLink?.setOnClickListener {
            openWebPage(context, context.getString(R.string.bmpro_landing_url))
        }

        val logoBmpro = footerView.findViewById<View>(R.id.footer_logo_bmpro)
        logoBmpro?.setOnClickListener {
            openWebPage(context, context.getString(R.string.bmpro_web_url))
        }

        val facebook = footerView.findViewById<View>(R.id.footer_social_facebook)
        facebook?.setOnClickListener {
            openWebPage(context, context.getString(R.string.social_facebook_url))
        }

        val instagram = footerView.findViewById<View>(R.id.footer_social_instagram)
        instagram?.setOnClickListener {
            openWebPage(context, context.getString(R.string.social_instagram_url))
        }

        val youtube = footerView.findViewById<View>(R.id.footer_social_youtube)
        youtube?.setOnClickListener {
            openWebPage(context, context.getString(R.string.social_youtube_url))
        }

        val linkedin = footerView.findViewById<View>(R.id.footer_social_linkedin)
        linkedin?.setOnClickListener {
            openWebPage(context, context.getString(R.string.social_linkedin_url))
        }
    }

    /**
     * "Videos, guides and more at" on the first line, then the URL in BMPRO orange,
     * underlined and in Roboto Medium (as in the SmartSensePro demo build). The URL is
     * everything after the line break in `bmpro_landing_link`.
     */
    private fun styleLandingLink(textView: TextView) {
        val context = textView.context
        val text = context.getString(R.string.bmpro_landing_link)
        val urlStart = text.indexOf('\n') + 1
        if (urlStart <= 0 || urlStart >= text.length) return
        val styled = SpannableString(text).apply {
            val end = text.length
            setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.bmpro_orange)), urlStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), urlStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ResourcesCompat.getFont(context, R.font.roboto_w500)?.let {
                    setSpan(TypefaceSpan(it), urlStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        textView.text = styled
    }

    private fun openWebPage(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Unable to open web page: $url")
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }
}

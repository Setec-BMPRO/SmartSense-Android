package com.smartsense.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.smartsense.app.R
import timber.log.Timber

object FooterUtils {

    fun setupBmproFooter(footerView: View) {
        val context = footerView.context ?: return

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

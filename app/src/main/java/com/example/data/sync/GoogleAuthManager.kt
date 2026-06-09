package com.example.data.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope("https://www.googleapis.com/auth/spreadsheets"),
            Scope("https://www.googleapis.com/auth/drive.file")
        )
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isSignIn(): Boolean {
        return getSignedInAccount() != null
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getSignedInAccount() ?: return@withContext null
        try {
            val scope = "oauth2:https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file"
            GoogleAuthUtil.getToken(context, account.account!!, scope)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
            if (account != null) {
                val scope = "oauth2:https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file"
                val token = GoogleAuthUtil.getToken(context, account.account!!, scope)
                GoogleAuthUtil.clearToken(context, token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        signInClient.signOut()
    }
}

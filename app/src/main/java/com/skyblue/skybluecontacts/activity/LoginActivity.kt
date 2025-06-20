package com.skyblue.skybluecontacts.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.skyblue.skybluecontacts.session.SessionHandler
import com.skyblue.skybluecontacts.util.AppConstants.SHARED_PREF
import com.skyblue.skybluecontacts.BaseActivity
import com.skyblue.skybluecontacts.R
import com.skyblue.skybluecontacts.RoomContactsActivity
import com.skyblue.skybluecontacts.databinding.ActivityLoginBinding
import com.skyblue.skybluecontacts.model.Login
import com.skyblue.skybluecontacts.retrofit.RetrofitInstance
import com.skyblue.skybluecontacts.util.showMessage
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var context: Context = this@LoginActivity
    private val TAG = "GoogleSignIn_"
    lateinit var session: SessionHandler
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initTheme()

        auth = Firebase.auth
        credentialManager = CredentialManager.create(baseContext)

        session = SessionHandler
        session.init(this)

        if(session.isLoggedIn()){
            val intent = Intent(context, RoomContactsActivity::class.java)
            startActivity(intent)
            finish()
        }

        onClick()
    }

    private fun onClick() {
        binding.google.setOnClickListener(){
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(getString(R.string.client_id))
                .setFilterByAuthorizedAccounts(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            lifecycleScope.launch {
                try {
                    // Launch Credential Manager UI
                    val result = credentialManager.getCredential(
                        context = baseContext,
                        request = request
                    )

                    // Extract credential from the result returned by Credential Manager
                    handleSignIn(result.credential)
                } catch (e: GetCredentialException) {
                    Log.e(TAG, "Couldn't retrieve user's credentials: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        Log.e("GoogleSignIn_", "User: ${user.toString()}")
        Log.e("GoogleSignIn_", "DisplayName: ${user?.displayName}")
        Log.e("GoogleSignIn_", "Email: ${user?.email}")
        Log.e("GoogleSignIn_", "UID: ${user?.uid}")
        Log.e("GoogleSignIn_", "PhotoURL: ${user?.photoUrl}")
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            // Sign in to Firebase with using the token
            Log.e(TAG, "Google ID Token: ${googleIdTokenCredential.idToken}")
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.e(TAG, "Started Firebase Auth With Google: $idToken")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)

                    showMessage(getString(R.string.google_sign_in_success))
                    binding.googleSignInLayout.visibility = View.GONE
                    binding.loginInitLayout.visibility = View.VISIBLE
                    loginNow(user?.uid.orEmpty(), user?.displayName.orEmpty(), user?.email.orEmpty())
                } else {
                    Log.e(TAG, "signInWithCredential:failure", task.exception)
                    updateUI(null)
                    showMessage(
                        getString(
                            R.string.google_sign_in_failed,
                            task.exception?.localizedMessage
                        ))
                }
            }
    }

    data class UserResponse(
        val userId: String
    )

    private fun loginNow(googleId: String, displayName: String, email: String) {
        val currentDate: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val currentTime: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentDateTime = "$currentDate $currentTime"

        val jsonObject = JSONObject().apply {
            put("acc", "login")
            put("googleId", googleId)
            put("email", email)
            put("displayName", displayName)
            put("dateTime", currentDateTime)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        RetrofitInstance.apiInterface.login(requestBody).enqueue(object : Callback<Login> {
            override fun onResponse(call: Call<Login>, response: Response<Login>) {
                if (response.isSuccessful) {
                    val login = response.body()
                    val status: Boolean = login?.status == "true"

                    if (status){
                        val userId = login?.response?.getOrNull(0)?.userId
                        if (login != null) {

                            Log.e("Login_", userId.toString())

                                session.loginUser(userId.toString(), displayName)
                                val intent = Intent(context, RoomContactsActivity::class.java)
                                intent.putExtra("userId", userId.toString())
                                intent.putExtra("displayName", displayName)
                                startActivity(intent)
                                finish()
                            showMessage(login.message)
                        }
                    }else {
                        showMessage(getString(R.string.login_failed))
                    }
                } else {
                    showMessage(getString(R.string.login_failed))
                }
            }

            override fun onFailure(call: Call<Login>, t: Throwable) {
                showMessage(getString(R.string.login_failed))
            }
        })
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser

      if (currentUser != null){
          startActivity(
              Intent(
                  this, RoomContactsActivity
                  ::class.java
              )
          )
          finish()
      } else{
          Log.e("GoogleSignIn_", "User is null")
      }
    }

    private fun initTheme() {
        val sharedPreferences = getSharedPreferences(
            SHARED_PREF,
            MODE_PRIVATE
        )

        val isDarkModeOn = sharedPreferences.getBoolean("isDarkModeOn", false)

        if (isDarkModeOn) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

//    override fun attachBaseContext(newBase: Context) {
//        val lang = PreferenceHelper.getLanguage(newBase)
//        val context = LocaleHelper.setLocale(newBase, lang)
//        super.attachBaseContext(context)
//    }

}
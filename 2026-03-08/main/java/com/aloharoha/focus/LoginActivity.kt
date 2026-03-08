package com.aloharoha.focus

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import android.util.Log

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val GOOGLE_SIGN_IN = 9001
    // LoginActivity.kt의 onCreate 부분 수정
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("840687477850-u0bfkvnc7lf2qvnt8em2j3aga3lj3u57.apps.googleusercontent.com") // 직접 따옴표 안에 넣어줘!
        .requestEmail()
        .build()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 무조건 로그인 화면 레이아웃을 먼저 세팅해!
        setContentView(R.layout.activity_login)

        // Firebase 인증 객체 초기화
        auth = Firebase.auth
        val user = auth.currentUser

        // 2. 만약 이미 로그인이 되어 있다면? 바로 메인 화면으로 보내버려!
        if (user != null) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // 로그인 창은 닫아버리기! ㅋ꙼̈ㅋ̆̎ㅋ̐̈ㅋ̊̈ㅋ̄̈
            return
        }

        // 3. 로그인이 필요한 상태니까 아래 버튼 연결 코드들이 실행돼!
        Log.d("ROHA_LOG", "로그인 대기 중... 응ᩚ!")

        // --- [로하가 준 로그인 관련 전체 코드 시작] ---

        // 구글 로그인 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("840687477850-u0bfkvnc7lf2qvnt8em2j3aga3lj3u57.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 버튼 연결 (findViewById) - XML에 이 ID들이 꼭 있어야 해!
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val btnGoogle = findViewById<Button>(R.id.btnGoogleLogin)
        val btnMs = findViewById<Button>(R.id.btnMsLogin)

        // --- [이메일 로그인] ---
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                signInWithEmail(email, password)
            }
        }

        // --- [이메일 회원가입 + 인증메일] ---
        btnSignUp.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                signUpWithEmail(email, password)
            }
        }

        // --- [구글 로그인] ---
        btnGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, GOOGLE_SIGN_IN)
        }

        // --- [마이크로소프트 로그인] ---
        btnMs.setOnClickListener {
            signInWithMicrosoft()
        }
    }

    // 이메일 로그인 처리
    private fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user?.isEmailVerified == true) {
                    Toast.makeText(this, "로그인 성공! 응ᩚ!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    Toast.makeText(this, "이메일 인증을 먼저 완료해줘! ㆅㆅ", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "로그인 실패ㅠㅠ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 회원가입 및 인증메일 발송
    private fun signUpWithEmail(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener {
                    Toast.makeText(this, "인증 메일을 보냈어! 확인해봐! 응ᩚ!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 마이크로소프트 로그인 (OAuth)
    private fun signInWithMicrosoft() {
        val provider = OAuthProvider.newBuilder("microsoft.com")
        auth.startActivityForSignInWithProvider(this, provider.build())
            .addOnSuccessListener {
                Toast.makeText(this, "MS 로그인 성공! ㅋ꙼̈ㅋ̆̎ㅋ̐̈ㅋ̊̈ㅋ̄̈", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
            }
            .addOnFailureListener {
                Toast.makeText(this, "MS 로그인 실패ㅠㅠ", Toast.LENGTH_SHORT).show()
            }
    }

    // 구글 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                // 여기서 Firebase Credential로 연결하는 작업이 더 필요해!
                Toast.makeText(this, "구글 로그인 성공!", Toast.LENGTH_SHORT).show()
            } catch (e: ApiException) {
                Toast.makeText(this, "구글 로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
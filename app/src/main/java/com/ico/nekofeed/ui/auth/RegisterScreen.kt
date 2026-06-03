package com.ico.nekofeed.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.ico.nekofeed.ui.theme.ExpressiveTokens

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onRegisterSuccess()
        }
    }

    RegisterScreenContent(
        uiState = uiState,
        username = username,
        password = password,
        confirmPassword = confirmPassword,
        passwordVisible = passwordVisible,
        confirmPasswordVisible = confirmPasswordVisible,
        onUsernameChange = { username = it; viewModel.clearError() },
        onPasswordChange = { password = it; viewModel.clearError() },
        onConfirmPasswordChange = { confirmPassword = it; viewModel.clearError() },
        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
        onConfirmPasswordVisibilityToggle = { confirmPasswordVisible = !confirmPasswordVisible },
        onRegister = {
            if (password == confirmPassword) {
                viewModel.register(username, password)
            }
        },
        onNavigateToLogin = onNavigateToLogin,
        focusManager = focusManager
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegisterScreenContent(
    uiState: AuthUiState,
    username: String,
    password: String,
    confirmPassword: String,
    passwordVisible: Boolean,
    confirmPasswordVisible: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onConfirmPasswordVisibilityToggle: () -> Unit,
    onRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager = LocalFocusManager.current
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "NekoFeed",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "创建新账户",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("用户名") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("密码") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("确认密码") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onConfirmPasswordVisibilityToggle) {
                    Icon(
                        if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onRegister()
            }),
            modifier = Modifier.fillMaxWidth()
        )

        if (password != confirmPassword && confirmPassword.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "两次密码不一致",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRegister,
            enabled = !uiState.isLoading && password == confirmPassword,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(ExpressiveTokens.SplitButtonOuterCorner)
        ) {
            if (uiState.isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("注册", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("已有账户？登录")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    MaterialTheme {
        RegisterScreenContent(
            uiState = AuthUiState(),
            username = "",
            password = "",
            confirmPassword = "",
            passwordVisible = false,
            confirmPasswordVisible = false,
            onUsernameChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onConfirmPasswordVisibilityToggle = {},
            onRegister = {},
            onNavigateToLogin = {}
        )
    }
}

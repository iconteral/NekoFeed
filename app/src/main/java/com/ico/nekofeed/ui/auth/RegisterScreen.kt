package com.ico.nekofeed.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ico.nekofeed.ui.theme.NekoFeedTheme

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
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn && !hasNavigated) {
            hasNavigated = true
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
        onUsernameChange = {
            username = it
            viewModel.clearError()
        },
        onPasswordChange = {
            password = it
            viewModel.clearError()
        },
        onConfirmPasswordChange = {
            confirmPassword = it
            viewModel.clearError()
        },
        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
        onConfirmPasswordVisibilityToggle = {
            confirmPasswordVisible = !confirmPasswordVisible
        },
        onRegister = {
            if (password == confirmPassword) {
                viewModel.register(username, password)
            }
        },
        onNavigateToLogin = onNavigateToLogin
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
    onNavigateToLogin: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    AuthPage(
        title = "创建 NekoFeed 账号",
        subtitle = "注册后可跨设备同步互动数据"
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("用户名") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        PasswordField(
            value = password,
            label = "密码",
            visible = passwordVisible,
            imeAction = ImeAction.Next,
            onValueChange = onPasswordChange,
            onVisibilityToggle = onPasswordVisibilityToggle,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        PasswordField(
            value = confirmPassword,
            label = "确认密码",
            visible = confirmPasswordVisible,
            imeAction = ImeAction.Done,
            onValueChange = onConfirmPasswordChange,
            onVisibilityToggle = onConfirmPasswordVisibilityToggle,
            onImeAction = {
                focusManager.clearFocus()
                onRegister()
            }
        )
        if (password != confirmPassword && confirmPassword.isNotEmpty()) {
            AuthError("两次密码不一致")
        }
        AuthError(uiState.error)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRegister,
            enabled = !uiState.isLoading &&
                password == confirmPassword &&
                password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
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
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onNavigateToLogin) {
            Text("已有账号？登录")
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    visible: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onVisibilityToggle: () -> Unit,
    onImeAction: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    imageVector = if (visible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = if (visible) "隐藏密码" else "显示密码"
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = if (imeAction == ImeAction.Done) {
            KeyboardActions(onDone = { onImeAction() })
        } else {
            KeyboardActions(onNext = { onImeAction() })
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true, name = "Register Light")
@Preview(showBackground = true, name = "Register Dark", uiMode = 0x20)
@Composable
private fun RegisterScreenPreview() {
    NekoFeedTheme {
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

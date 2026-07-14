package com.example.uzhavan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uzhavan.ui.theme.*

object AgriTheme {
    val GreenGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F4226), Color(0xFF1A6B3C), Color(0xFF1E7D45))
    )
    val CardGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF7F7F5))
    )
    val ButtonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1A6B3C), Color(0xFF2E9E5B))
    )
    val TopBarGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0F4226), Color(0xFF1A6B3C))
    )
}

@Composable
fun AgriCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x0A000000))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, AgriDivider, RoundedCornerShape(20.dp))
            .padding(24.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun AgriButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor         = AgriGreen,
            disabledContainerColor = Color(0xFFCCCCCC)
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text       = text,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 15.sp,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun AgriTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label, color = AgriGrayLight, fontSize = 13.sp) },
        modifier        = modifier.fillMaxWidth(),
        trailingIcon    = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine      = singleLine,
        shape           = RoundedCornerShape(12.dp),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedTextColor     = AgriGreenDark,
            unfocusedTextColor   = AgriGreenDark,
            focusedBorderColor   = AgriGreen,
            unfocusedBorderColor = AgriDivider,
            focusedLabelColor    = AgriGreen,
            unfocusedLabelColor  = AgriGrayLight,
            cursorColor          = AgriGreen,
            focusedContainerColor   = Color.White,
            unfocusedContainerColor = Color(0xFFFAFAFA)
        )
    )
}

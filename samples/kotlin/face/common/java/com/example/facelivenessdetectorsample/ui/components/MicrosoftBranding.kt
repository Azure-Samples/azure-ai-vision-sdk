package com.microsoft.azure.ai.vision.facelivenessdetectorsample.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MicrosoftBranding(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageName = context.packageName

    val microsoftLogoRes = try {
        val resourceName = "microsoft_logo"
        val resourceId = context.resources.getIdentifier(
            resourceName, 
            "drawable", 
            packageName
        )
        if (resourceId != 0) {
            resourceId
        } else {
            context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        }
    } catch (e: Exception) {
        context.resources.getIdentifier("microsoft_logo", "drawable", context.packageName)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = microsoftLogoRes),
            contentDescription = "Microsoft logo",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(com.azure.android.ai.vision.face.ui.R.string.AZAIF_MicrosoftBranding),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
